package dev.agentshell

import dev.agentshell.approval.ApprovalServer
import dev.agentshell.audit.SqliteAuditTrail
import dev.agentshell.executor.ToolDispatcher
import dev.agentshell.llm.GeminiGateway
import dev.agentshell.llm.LlmGateway
import dev.agentshell.llm.OllamaGateway
import dev.agentshell.llm.OpenAiGateway
import dev.agentshell.llm.OpenRouterGateway
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
import dev.agentshell.workflow.WorkflowLoader
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("dev.agentshell.Main")

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

    val watchdog = WatchdogService(store, auditTrail = auditTrail).also { it.start() }

    val approvalServer = config.approvalPort?.let { port ->
        ApprovalServer(approvalGate, port).also {
            it.start()
            println("Approval server: http://localhost:$port/approvals")
        }
    }

    val runner = AgentRunner(
        stateStore = store,
        dispatcher = dispatcher,
        riskScorer = RiskScorer(),
        approvalGate = approvalGate,
        idempotency = IdempotencyService(),
        heartbeat = heartbeat,
        runtime = AgentRuntime(store),
        auditTrail = auditTrail,
        gateway = config.gateway,
    )

    Runtime.getRuntime().addShutdownHook(Thread({
        log.info("Shutdown hook triggered — closing resources")
        approvalServer?.stop()
        watchdog.stop()
        heartbeat.shutdown()
        db.close()
        log.info("AgentShell shut down cleanly")
    }, "agentshell-shutdown"))

    val outcome = when (config.mode) {
        RunMode.AGENTIC -> {
            requireNotNull(config.goal) { "--goal is required for --agentic mode" }
            requireNotNull(config.gateway) { "--provider is required for --agentic mode" }
            runner.executeAgentic(
                AgentRunner.AgenticConfig(
                    agentId = config.agentId,
                    goal = config.goal,
                    riskThreshold = config.riskThreshold,
                    maxIterations = config.maxIterations,
                )
            ).also { if (it.finalMessage != null) println("\n=== Agent response ===\n${it.finalMessage}\n=====================") }
        }
        RunMode.SCRIPTED -> runner.execute(config.runConfig!!)
    }

    println("Run finished: runId=${outcome.runId} status=${outcome.status} steps=${outcome.results.size}")
    log.info("Run outcome: runId={} status={} steps={}", outcome.runId, outcome.status, outcome.results.size)
    exitProcess(if (outcome.status.name == "COMPLETED") 0 else 1)
}

enum class RunMode { SCRIPTED, AGENTIC }

private data class CliConfig(
    val agentId: String,
    val dbPath: String,
    val approvalTtlMs: Long,
    val approvalPort: Int?,
    val riskThreshold: Int,
    val mode: RunMode,
    // SCRIPTED mode
    val runConfig: AgentRunner.RunConfig?,
    // AGENTIC mode
    val gateway: LlmGateway?,
    val goal: String?,
    val maxIterations: Int,
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

            Scripted mode (workflow file):
              --workflow <file>         Load steps from .yaml/.yml/.json
              --demo                    Run built-in demo workflow

            Agentic mode (LLM-driven):
              --agentic                 Enable LLM-driven agentic loop
              --goal <text>             Task description for the agent
              --provider <name>         LLM provider: openai | openrouter | gemini | ollama
              --model <name>            Model override (e.g. gpt-4o, gemini-2.0-flash)
              --ollama-url <url>        Ollama base URL (default: http://localhost:11434)
              --max-iterations <n>      Max agentic loop iterations (default: 50)

            Common options:
              --db <path>               SQLite database path
              --risk-threshold <0-100>  Approval threshold (default: 60)
              --approval-ttl-ms <ms>    Approval TTL (default: 3600000)
              --approval-port <port>    Start approval HTTP server
        """.trimIndent())
        return null
    }

    val dbPath = map["db"] ?: "${System.getProperty("user.home")}/.agentshell/${agentId}.db"
    File(dbPath).parentFile?.mkdirs()

    val riskThreshold = map["risk-threshold"]?.toIntOrNull() ?: 60
    val approvalTtlMs = map["approval-ttl-ms"]?.toLongOrNull() ?: ApprovalGate.DEFAULT_TTL_MS
    val approvalPort = map["approval-port"]?.toIntOrNull()
    val maxIterations = map["max-iterations"]?.toIntOrNull() ?: 50

    // ─── Agentic mode ──────────────────────────────────────────────────────
    if (map.containsKey("agentic")) {
        val goal = map["goal"] ?: run {
            System.err.println("Error: --goal <text> is required in --agentic mode")
            return null
        }
        val provider = map["provider"] ?: run {
            System.err.println("Error: --provider <openai|openrouter|gemini|ollama> is required in --agentic mode")
            return null
        }
        val model = map["model"]
        val gateway = buildGateway(provider, model, map) ?: return null
        return CliConfig(
            agentId = agentId, dbPath = dbPath, approvalTtlMs = approvalTtlMs,
            approvalPort = approvalPort, riskThreshold = riskThreshold,
            mode = RunMode.AGENTIC, runConfig = null,
            gateway = gateway, goal = goal, maxIterations = maxIterations,
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
        approvalPort = approvalPort, riskThreshold = riskThreshold,
        mode = RunMode.SCRIPTED, runConfig = runConfig,
        gateway = null, goal = null, maxIterations = maxIterations,
    )
}

private fun buildGateway(provider: String, model: String?, map: Map<String, String>): LlmGateway? = when (provider.lowercase()) {
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
        System.err.println("Error: unknown provider '$provider'. Use: openai | openrouter | gemini | ollama")
        null
    }
}
