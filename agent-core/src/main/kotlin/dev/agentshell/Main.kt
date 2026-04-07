package dev.agentshell

import dev.agentshell.approval.ApprovalServer
import dev.agentshell.audit.SqliteAuditTrail
import dev.agentshell.executor.ToolDispatcher
import dev.agentshell.llm.GeminiGateway
import dev.agentshell.llm.LlmGateway
import dev.agentshell.llm.OllamaGateway
import dev.agentshell.llm.OpenAiGateway
import dev.agentshell.llm.OpenRouterGateway
import dev.agentshell.llm.provider.LlmProviderAdapter
import dev.agentshell.llm.provider.LlmProviderRegistry
import dev.agentshell.llm.provider.LlmProvidersLoader
import dev.agentshell.memory.MemoryAugmentedGateway
import dev.agentshell.memory.MemoryStore
import dev.agentshell.memory.SqliteMemoryStore
import dev.agentshell.observability.MetricsServer
import dev.agentshell.orchestrator.Orchestrator
import dev.agentshell.orchestrator.OrchestratorPlanLoader
import dev.agentshell.plugin.PluginLoader
import dev.agentshell.report.RunReportGenerator
import dev.agentshell.runtime.AgentRunner
import dev.agentshell.runtime.AgentRuntime
import dev.agentshell.runtime.ApprovalGate
import dev.agentshell.runtime.HeartbeatService
import dev.agentshell.runtime.IdempotencyService
import dev.agentshell.runtime.RiskScorer
import dev.agentshell.runtime.WatchdogService
import dev.agentshell.security.SecretsVault
import dev.agentshell.state.StateStore
import dev.agentshell.state.sqlite.DatabaseManager
import dev.agentshell.state.sqlite.SqliteStateStore
import dev.agentshell.workflow.AgentPresetLoader
import dev.agentshell.workflow.WorkflowLoader
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("dev.agentshell.Main")
private var dynamicProvidersInitialized = false
private var pluginLoader: PluginLoader? = null

fun main(args: Array<String>) {
    val config = parseArgs(args) ?: exitProcess(1)

    // Register secrets for masking in logs/audit before anything else
    config.gateway?.let { SecretsVault.registerLlmKeys() }

    log.info("AgentShell starting — agentId={} db={} mode={}", config.agentId, config.dbPath, config.mode)
    println("AgentShell v3.0 | agentId=${config.agentId} | db=${config.dbPath} | mode=${config.mode}")

    val db = DatabaseManager(config.dbPath)
    val store: StateStore = SqliteStateStore(db)
    val auditTrail = SqliteAuditTrail(db)
    val dispatcher = ToolDispatcher()
    val heartbeat = HeartbeatService(store)
    val approvalGate = ApprovalGate(ttlMs = config.approvalTtlMs)

    val memoryStore: MemoryStore? = if (config.enableMemory) {
        SqliteMemoryStore(db).also { println("Memory:          enabled (SQLite)") }
    } else null

    val watchdog = WatchdogService(store, auditTrail = auditTrail).also { it.start() }

    val approvalServer = config.approvalPort?.let { port ->
        ApprovalServer(approvalGate, port).also {
            it.start()
            println("Approval server: http://localhost:$port/approvals")
        }
    }

    val metricsServer = config.metricsPort?.let { port ->
        val reportGen = RunReportGenerator(store, auditTrail)
        MetricsServer(port, reportGen, store, memoryStore).also {
            it.start()
            println("Dashboard:       http://localhost:$port/ui")
            println("Metrics server:  http://localhost:$port/metrics")
            println("Run reports:     http://localhost:$port/report/{runId}")
        }
    }

    // Wrap gateway with memory augmentation if enabled
    val effectiveGateway = if (config.gateway != null && memoryStore != null) {
        MemoryAugmentedGateway(config.gateway, memoryStore).also {
            println("Memory:          augmenting gateway with past experiences")
        }
    } else config.gateway

    val runner = AgentRunner(
        stateStore = store,
        dispatcher = dispatcher,
        riskScorer = RiskScorer(),
        approvalGate = approvalGate,
        idempotency = IdempotencyService(),
        heartbeat = heartbeat,
        runtime = AgentRuntime(store),
        auditTrail = auditTrail,
        gateway = effectiveGateway,
    )

    Runtime.getRuntime().addShutdownHook(Thread({
        log.info("Shutdown hook triggered — closing resources")
        approvalServer?.stop()
        metricsServer?.stop()
        pluginLoader?.unloadAll()
        watchdog.stop()
        heartbeat.shutdown()
        db.close()
        log.info("AgentShell shut down cleanly")
    }, "agentshell-shutdown"))

    // ORCHESTRATE mode — run an OrchestratorPlan
    if (config.mode == RunMode.ORCHESTRATE) {
        requireNotNull(config.orchestratePlanPath) { "--orchestrate requires a plan file path" }
        val plan = OrchestratorPlanLoader.load(config.orchestratePlanPath)
        println("Orchestrating plan '${plan.name}' with ${plan.agents.size} agents...")

        val orchestrator = Orchestrator(
            stateStore = store,
            gatewayFactory = { spec ->
                val provider = spec.provider.ifBlank { config.orchestrateProvider }
                val model = spec.model ?: config.orchestrateModel
                buildGateway(provider, model, emptyMap())
            },
            auditTrailFactory = { _ -> auditTrail },
            memoryStore = memoryStore
        )
        val orchResult = orchestrator.run(plan)
        orchestrator.shutdown()

        println("\n=== Orchestration Result ===")
        orchResult.agentResults.forEach { r ->
            println("  ${r.agentId}: ${r.status}${if (r.summary != null) " — ${r.summary.take(80)}" else ""}")
        }
        println("============================")
        exitProcess(if (orchResult.success) 0 else 1)
    }

    val outcome = when (config.mode) {
        RunMode.AGENTIC -> {
            requireNotNull(config.goal) { "--goal is required for --agentic mode" }
            requireNotNull(effectiveGateway) { "--provider is required for --agentic mode" }
            runner.executeAgentic(
                AgentRunner.AgenticConfig(
                    agentId = config.agentId,
                    goal = config.goal,
                    systemPrompt = config.agenticSystemPrompt,
                    riskThreshold = config.riskThreshold,
                    maxIterations = config.maxIterations,
                )
            ).also { if (it.finalMessage != null) println("\n=== Agent response ===\n${it.finalMessage}\n=====================") }
        }
        RunMode.SCRIPTED -> runner.execute(config.runConfig!!)
        RunMode.ORCHESTRATE -> error("unreachable")
    }

    println("Run finished: runId=${outcome.runId} status=${outcome.status} steps=${outcome.results.size}")
    log.info("Run outcome: runId={} status={} steps={}", outcome.runId, outcome.status, outcome.results.size)
    exitProcess(if (outcome.status.name == "COMPLETED") 0 else 1)
}

private fun ensureDynamicProvidersLoaded() {
    if (dynamicProvidersInitialized) return

    val providersConfig = LlmProvidersLoader.load()
    LlmProvidersLoader.registerAll(providersConfig)

    val resolvedPluginDir = File(System.getProperty("agentshell.pluginsDir") ?: "plugins")
    pluginLoader = PluginLoader(resolvedPluginDir)
    val loadedPlugins = pluginLoader?.loadAll().orEmpty()
    if (loadedPlugins.isNotEmpty()) {
        println("Plugins:         loaded ${loadedPlugins.size} from ${resolvedPluginDir.absolutePath}")
    }

    dynamicProvidersInitialized = true
}

enum class RunMode { SCRIPTED, AGENTIC, ORCHESTRATE }

private data class CliConfig(
    val agentId: String,
    val dbPath: String,
    val approvalTtlMs: Long,
    val approvalPort: Int?,
    val metricsPort: Int?,
    val riskThreshold: Int,
    val mode: RunMode,
    // SCRIPTED mode
    val runConfig: AgentRunner.RunConfig?,
    // AGENTIC mode
    val gateway: LlmGateway?,
    val goal: String?,
    val maxIterations: Int,
    val agenticSystemPrompt: String = AgentRunner.DEFAULT_SYSTEM_PROMPT,
    // MEMORY
    val enableMemory: Boolean = false,
    // ORCHESTRATE mode
    val orchestratePlanPath: String? = null,
    val orchestrateProvider: String = "ollama",
    val orchestrateModel: String? = null,
)

private fun parseArgs(args: Array<String>): CliConfig? {
    val map = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        if (arg.startsWith("--")) {
            val key = arg.removePrefix("--")
            val nextIsValue = i + 1 < args.size && !args[i + 1].startsWith("--")
            if (nextIsValue) { map[key] = args[i + 1]; i += 2 }
            else { map[key] = "true"; i++ }
        } else { i++ }
    }

    val agentId = map["agent"] ?: run {
        System.err.println("""
            Usage: agentshell --agent <id> [options]

            Preset mode (ready-made agents):
              --preset <name>           Use a built-in agent preset
                                        Built-in: code-review, git-workflow, project-scan
              --preset <path.yaml>      Load preset from a file

            Agentic mode (LLM-driven):
              --agentic                 Enable LLM-driven agentic loop
              --goal <text>             Task description for the agent
              --provider <name>         LLM provider: openai | openrouter | gemini | ollama
              --model <name>            Model override (e.g. gpt-4o, gemini-2.0-flash)
              --ollama-url <url>        Ollama base URL (default: http://localhost:11434)
              --max-iterations <n>      Max agentic loop iterations (default: 50)
              --memory                  Enable agent memory (injects past run context)

            Multi-agent orchestration:
              --orchestrate <plan.yaml> Run an orchestrator plan (multi-agent pipeline)
              --memory                  Share memory store across all agents in plan
              --provider <name>         Default provider for agents (can override per-agent in YAML)
              --model <name>            Default model for agents

            Scripted mode (workflow file):
              --workflow <file>         Load steps from .yaml/.yml/.json
              --demo                    Run built-in demo workflow

            Common options:
              --db <path>               SQLite database path
              --risk-threshold <0-100>  Approval threshold (default: 60)
              --approval-ttl-ms <ms>    Approval TTL (default: 3600000)
              --approval-port <port>    Start approval HTTP server (default: disabled)
              --metrics-port <port>     Start metrics+UI server (default: disabled)
        """.trimIndent())
        return null
    }

    val dbPath = map["db"] ?: "${System.getProperty("user.home")}/.agentshell/${agentId}.db"
    File(dbPath).parentFile?.mkdirs()

    val riskThreshold = map["risk-threshold"]?.toIntOrNull() ?: 60
    val approvalTtlMs = map["approval-ttl-ms"]?.toLongOrNull() ?: ApprovalGate.DEFAULT_TTL_MS
    val approvalPort = map["approval-port"]?.toIntOrNull()
    val metricsPort  = map["metrics-port"]?.toIntOrNull()
    val maxIterations = map["max-iterations"]?.toIntOrNull() ?: 50

    // ─── Orchestrate mode ──────────────────────────────────────────────────
    if (map.containsKey("orchestrate")) {
        val planPath = map["orchestrate"]!!
        val enableMemory = map.containsKey("memory")
        return CliConfig(
            agentId = agentId, dbPath = dbPath, approvalTtlMs = approvalTtlMs,
            approvalPort = approvalPort, metricsPort = metricsPort, riskThreshold = riskThreshold,
            mode = RunMode.ORCHESTRATE, runConfig = null,
            gateway = null, goal = null, maxIterations = maxIterations,
            enableMemory = enableMemory,
            orchestratePlanPath = planPath,
            orchestrateProvider = map["provider"] ?: "ollama",
            orchestrateModel = map["model"],
        )
    }

    // ─── Agentic mode ──────────────────────────────────────────────────────
    if (map.containsKey("agentic") || map.containsKey("preset")) {
        // --preset loads goal+systemPrompt from YAML, --goal overrides
        var goal: String? = map["goal"]
        var systemPrompt = AgentRunner.DEFAULT_SYSTEM_PROMPT
        var presetMaxIter = maxIterations
        var presetThreshold = riskThreshold

        if (map.containsKey("preset")) {
            val presetName = map["preset"]!!
            val preset = try { AgentPresetLoader.load(presetName) } catch (e: Exception) {
                System.err.println("Error loading preset '$presetName': ${e.message}")
                System.err.println("Built-in presets: ${AgentPresetLoader.listBuiltIn().joinToString(", ")}")
                return null
            }
            if (goal == null) goal = preset.goal.trim()
            systemPrompt = preset.systemPrompt.trim().ifBlank { systemPrompt }
            presetMaxIter = map["max-iterations"]?.toIntOrNull() ?: preset.maxIterations
            presetThreshold = map["risk-threshold"]?.toIntOrNull() ?: preset.riskThreshold
            println("Loaded preset '${preset.name}': ${preset.description}")
        }

        if (goal == null) {
            System.err.println("Error: --goal <text> is required in --agentic mode (or use --preset <name>)")
            return null
        }
        val provider = map["provider"] ?: run {
            System.err.println("Error: --provider <openai|openrouter|gemini|ollama> is required in --agentic mode")
            return null
        }
        val model = map["model"]
        val gateway = buildGateway(provider, model, map) ?: return null
        val enableMemory = map.containsKey("memory")
        return CliConfig(
            agentId = agentId, dbPath = dbPath, approvalTtlMs = approvalTtlMs,
            approvalPort = approvalPort, metricsPort = metricsPort, riskThreshold = presetThreshold,
            mode = RunMode.AGENTIC, runConfig = null,
            gateway = gateway, goal = goal, maxIterations = presetMaxIter,
            agenticSystemPrompt = systemPrompt,
            enableMemory = enableMemory,
        )
    }

    // ─── Scripted mode ─────────────────────────────────────────────────────
    val runConfig: AgentRunner.RunConfig = when {
        map.containsKey("workflow") -> {
            val def = WorkflowLoader.load(map["workflow"]!!)
            println("Loaded workflow: '${def.name}' (${def.steps.size} steps)")
            WorkflowLoader.toRunConfig(agentId, def)
        }
        map.containsKey("demo") -> {
            val def = WorkflowLoader.load(
                object {}.javaClass.classLoader
                    .getResource("workflows/demo.yaml")
                    ?.path
                    ?: error("demo.yaml not found in classpath")
            )
            WorkflowLoader.toRunConfig(agentId, def)
        }
        else -> AgentRunner.RunConfig(agentId = agentId, steps = emptyList(), riskThreshold = riskThreshold)
    }.let { it.copy(riskThreshold = riskThreshold) }

    return CliConfig(
        agentId = agentId, dbPath = dbPath, approvalTtlMs = approvalTtlMs,
        approvalPort = approvalPort, metricsPort = metricsPort, riskThreshold = riskThreshold,
        mode = RunMode.SCRIPTED, runConfig = runConfig,
        gateway = null, goal = null, maxIterations = maxIterations,
    )
}

private fun buildGateway(provider: String, model: String?, map: Map<String, String>): LlmGateway? = when (provider.lowercase()) {
    else -> {
        ensureDynamicProvidersLoaded()
        LlmProviderRegistry.get(provider.lowercase())?.let { return LlmProviderAdapter(it) }

        when (provider.lowercase()) {
    "openai" -> {
        val key = System.getenv("OPENAI_API_KEY") ?: run {
            System.err.println("Error: OPENAI_API_KEY environment variable is not set")
            return null
        }
        OpenAiGateway(model = model ?: "gpt-4o-mini", apiKey = key)
    }
    "openrouter" -> {
        val key = System.getenv("OPENROUTER_API_KEY") ?: run {
            System.err.println("Error: OPENROUTER_API_KEY environment variable is not set")
            return null
        }
        OpenRouterGateway(model = model ?: "anthropic/claude-3-5-sonnet", apiKey = key)
    }
    "gemini" -> {
        val key = System.getenv("GEMINI_API_KEY") ?: System.getenv("GOOGLE_API_KEY") ?: run {
            System.err.println("Error: GEMINI_API_KEY or GOOGLE_API_KEY environment variable is not set")
            return null
        }
        GeminiGateway(model = model ?: "gemini-2.0-flash", apiKey = key)
    }
    "ollama" -> {
        val url = map["ollama-url"] ?: "http://localhost:11434"
        OllamaGateway(model = model ?: "llama3.1", baseUrl = url)
    }
            else -> {
                System.err.println("Error: unknown provider '$provider'. Use: openai | openrouter | gemini | ollama or a registered plugin provider")
                null
            }
        }
    }
}
