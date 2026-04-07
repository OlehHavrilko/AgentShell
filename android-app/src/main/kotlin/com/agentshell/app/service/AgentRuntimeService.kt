package com.agentshell.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.agentshell.app.AgentShellApp
import com.agentshell.app.ui.MainActivity
import com.agentshell.app.workflow.WorkflowDraftStep
import dev.agentshell.audit.AuditEvent
import dev.agentshell.audit.AuditEventType
import dev.agentshell.audit.AuditTrail
import dev.agentshell.executor.ToolDispatcher
import dev.agentshell.llm.LlmGateway
import dev.agentshell.runtime.AgentRunner
import dev.agentshell.state.InMemoryStateStore
import dev.agentshell.workflow.StepDefinition
import dev.agentshell.workflow.WorkflowDefinition
import dev.agentshell.workflow.WorkflowLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "AgentRuntimeService"
private const val NOTIFICATION_ID = 1

class AgentRuntimeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<AgentMessage>(extraBufferCapacity = 200)
    private val json = Json { ignoreUnknownKeys = true }

    private var prootSandbox: ProotSandbox? = null
    private var termuxConnector: TermuxConnector? = null
    private var mcpChannel: McpChannel? = null

    inner class LocalBinder : Binder() {
        val service get() = this@AgentRuntimeService
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("Agent runtime starting…"))
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_EMBEDDED -> startEmbedded()
            ACTION_START_TERMUX -> startExternal()
            ACTION_START_JVM -> startJvmAgent(
                goal = intent.getStringExtra(EXTRA_GOAL) ?: "Complete the task",
                providerId = intent.getStringExtra(EXTRA_PROVIDER) ?: "ollama"
            )
            ACTION_START_WORKFLOW -> startWorkflow(
                workflowJson = intent.getStringExtra(EXTRA_WORKFLOW_JSON).orEmpty()
            )
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    // ── Embedded Proot mode ────────────────────────────────────────────────────
    private fun startEmbedded() {
        val sandbox = ProotSandbox(this).also { prootSandbox = it }
        scope.launch {
            sandbox.start(outputToFlow = false)
                .onSuccess {
                    val ch = sandbox.openMcpChannel()
                    mcpChannel = ch
                    forwardEvents(ch)
                    updateNotification("Alpine sandbox running")
                }
                .onFailure { e -> Log.e(TAG, "Failed to start sandbox", e) }
        }
    }

    // ── Termux mode ────────────────────────────────────────────────────────────
    private fun startExternal() {
        val connector = TermuxConnector(this).also { termuxConnector = it }
        scope.launch {
            runCatching {
                connector.install()
                connector.startServer()
                delay(2_000)
                val ch = connector.connect()
                mcpChannel = ch
                forwardEvents(ch)
                updateNotification("Termux agent running")
            }.onFailure { e -> Log.e(TAG, "Termux connection failed", e) }
        }
    }

    // ── JVM-native agent mode ──────────────────────────────────────────────────
    /**
     * Runs [AgentRunner.executeAgentic] directly on the JVM (no Proot/Termux required).
     * Events are emitted via [_events] so ChatViewModel can observe them.
     */
    fun startJvmAgent(goal: String, providerId: String) {
        val gateway = buildGateway(providerId)
        if (gateway == null) {
            _events.tryEmit(AgentMessage("error", payload = "Unknown provider: $providerId"))
            return
        }

        scope.launch(Dispatchers.IO) {
            val stateStore = InMemoryStateStore()
            var runId = "(unknown)"
            val auditTrail = createEventAuditTrail { runId = it }

            val runner = AgentRunner(
                stateStore = stateStore,
                dispatcher = ToolDispatcher(),
                auditTrail = auditTrail,
                gateway = gateway,
            )

            updateNotification("Agent running [JVM/$providerId]…")
            try {
                val outcome = runner.executeAgentic(
                    AgentRunner.AgenticConfig(
                        agentId = "android-jvm",
                        goal = goal,
                        maxIterations = 30,
                    )
                )
                val finalMsg = outcome.finalMessage ?: "[Run complete — ${outcome.status}]"
                _events.tryEmit(AgentMessage("chat_response", outcome.runId, finalMsg))
            } catch (e: Exception) {
                Log.e(TAG, "JVM agent failed", e)
                _events.tryEmit(AgentMessage("error", runId, e.message))
            } finally {
                updateNotification("Agent idle")
            }
        }
    }

    fun startWorkflow(workflowJson: String) {
        val steps = runCatching {
            json.decodeFromString<List<WorkflowDraftStep>>(workflowJson)
        }.getOrElse { e ->
            _events.tryEmit(AgentMessage("error", payload = "Invalid workflow payload: ${e.message}"))
            return
        }

        if (steps.isEmpty()) {
            _events.tryEmit(AgentMessage("error", payload = "Workflow has no steps"))
            return
        }

        scope.launch(Dispatchers.IO) {
            val stateStore = InMemoryStateStore()
            var runId = "(unknown)"
            val auditTrail = createEventAuditTrail { runId = it }
            val runner = AgentRunner(
                stateStore = stateStore,
                dispatcher = ToolDispatcher(),
                auditTrail = auditTrail,
            )

            val workflow = WorkflowDefinition(
                name = "Android Workflow",
                description = "Generated from Workflow Builder",
                steps = steps.mapIndexed { index, step ->
                    StepDefinition(
                        id = "step_${index + 1}",
                        tool = step.type,
                        args = step.toArgsMap(),
                        description = step.params.take(120),
                    )
                },
            )

            updateNotification("Workflow running…")
            try {
                val outcome = runner.execute(WorkflowLoader.toRunConfig("android-workflow", workflow))
                val okCount = outcome.results.count { it.success }
                _events.tryEmit(
                    AgentMessage(
                        "chat_response",
                        outcome.runId,
                        "Workflow completed: ${outcome.status} ($okCount/${outcome.results.size} steps successful)",
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Workflow execution failed", e)
                _events.tryEmit(AgentMessage("error", runId, e.message))
            } finally {
                updateNotification("Agent idle")
            }
        }
    }

    /** Build an [AndroidLlmGateway] for the given provider using stored prefs. */
    private fun buildGateway(providerId: String): LlmGateway? {
        val prefs = getSharedPreferences("agentshell", Context.MODE_PRIVATE)
        return when (providerId) {
            "openai" -> AndroidLlmGateway(
                apiKey = prefs.getString("prov_apikey_openai", "") ?: "",
                model = prefs.getString("prov_model_openai", "gpt-4o-mini") ?: "gpt-4o-mini",
            ).takeIf { prefs.getString("prov_apikey_openai", "")!!.isNotBlank() }
            "ollama" -> AndroidLlmGateway(
                apiKey = "ollama",
                model = prefs.getString("prov_model_ollama", "llama3.2") ?: "llama3.2",
                baseUrl = (prefs.getString("prov_host_ollama", "http://localhost:11434") ?: "http://localhost:11434").trimEnd('/') + "/v1",
            )
            "groq" -> AndroidLlmGateway(
                apiKey = prefs.getString("prov_apikey_groq", "") ?: "",
                model = prefs.getString("prov_model_groq", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile",
                baseUrl = "https://api.groq.com/openai/v1",
            ).takeIf { prefs.getString("prov_apikey_groq", "")!!.isNotBlank() }
            "deepseek" -> AndroidLlmGateway(
                apiKey = prefs.getString("prov_apikey_deepseek", "") ?: "",
                model = prefs.getString("prov_model_deepseek", "deepseek-chat") ?: "deepseek-chat",
                baseUrl = "https://api.deepseek.com/v1",
            ).takeIf { prefs.getString("prov_apikey_deepseek", "")!!.isNotBlank() }
            "mistral" -> AndroidLlmGateway(
                apiKey = prefs.getString("prov_apikey_mistral", "") ?: "",
                model = prefs.getString("prov_model_mistral", "mistral-large-latest") ?: "mistral-large-latest",
                baseUrl = "https://api.mistral.ai/v1",
            ).takeIf { prefs.getString("prov_apikey_mistral", "")!!.isNotBlank() }
            "openrouter" -> AndroidLlmGateway(
                apiKey = prefs.getString("prov_apikey_openrouter", "") ?: "",
                model = prefs.getString("prov_model_openrouter", "anthropic/claude-3.5-sonnet") ?: "anthropic/claude-3.5-sonnet",
                baseUrl = "https://openrouter.ai/api/v1",
            ).takeIf { prefs.getString("prov_apikey_openrouter", "")!!.isNotBlank() }
            else -> null
        }
    }

    private fun forwardEvents(channel: McpChannel) = scope.launch {
        channel.receive().collect { msg -> _events.tryEmit(msg) }
    }

    private fun createEventAuditTrail(onRunId: (String) -> Unit): AuditTrail = object : AuditTrail {
        override fun record(event: AuditEvent) {
            val msg = when (event.eventType) {
                AuditEventType.RUN_STARTED -> {
                    onRunId(event.runId)
                    AgentMessage("run_started", event.runId, "agentId=${event.detail}")
                }
                AuditEventType.STEP_STARTED ->
                    AgentMessage("tool_start", event.runId, "${event.toolName} ${event.argsJson?.take(120)}")
                AuditEventType.STEP_COMPLETED ->
                    AgentMessage("tool_result", event.runId, event.resultJson?.take(200))
                AuditEventType.STEP_FAILED ->
                    AgentMessage("tool_error", event.runId, "${event.errorCode}: ${event.detail}")
                AuditEventType.APPROVAL_REQUESTED ->
                    AgentMessage("approval_request", event.runId, "${event.stepId}|${event.detail}")
                AuditEventType.RUN_COMPLETED ->
                    AgentMessage("run_complete", event.runId, event.detail)
                AuditEventType.RUN_FAILED, AuditEventType.RUN_CRASHED ->
                    AgentMessage("error", event.runId, event.detail)
                else -> return
            }
            _events.tryEmit(msg)
        }

        override fun queryByRun(runId: String): List<AuditEvent> = emptyList()
    }

    fun send(msg: AgentMessage) = scope.launch { mcpChannel?.send(msg) }

    fun events(): Flow<AgentMessage> = _events.asSharedFlow()

    fun stopRuntime() {
        prootSandbox?.stop()
        termuxConnector?.disconnect()
        mcpChannel?.close()
        mcpChannel = null
        updateNotification("Agent runtime stopped")
    }

    override fun onDestroy() {
        stopRuntime()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AgentShellApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AgentShell")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_START_EMBEDDED = "com.agentshell.action.START_EMBEDDED"
        const val ACTION_START_TERMUX = "com.agentshell.action.START_TERMUX"
        const val ACTION_START_JVM = "com.agentshell.action.START_JVM"
        const val ACTION_START_WORKFLOW = "com.agentshell.action.START_WORKFLOW"
        const val ACTION_STOP = "com.agentshell.action.STOP"
        const val EXTRA_GOAL = "goal"
        const val EXTRA_PROVIDER = "providerId"
        const val EXTRA_WORKFLOW_JSON = "workflowJson"
    }
}
