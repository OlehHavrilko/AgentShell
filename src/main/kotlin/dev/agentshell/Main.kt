package dev.agentshell

import dev.agentshell.domain.RiskLevel
import dev.agentshell.domain.ToolCall
import dev.agentshell.domain.ToolContract
import dev.agentshell.executor.ToolDispatcher
import dev.agentshell.runtime.AgentRunner
import dev.agentshell.runtime.AgentRuntime
import dev.agentshell.runtime.ApprovalGate
import dev.agentshell.runtime.HeartbeatService
import dev.agentshell.runtime.IdempotencyService
import dev.agentshell.runtime.RiskScorer
import dev.agentshell.state.StateStore
import dev.agentshell.state.sqlite.DatabaseManager
import dev.agentshell.state.sqlite.SqliteStateStore
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("dev.agentshell.Main")

fun main(args: Array<String>) {
    val config = parseArgs(args) ?: exitProcess(1)

    log.info("AgentShell starting — agentId={} db={}", config.agentId, config.dbPath)
    println("AgentShell v1.0 | agentId=${config.agentId} | db=${config.dbPath}")

    val db = DatabaseManager(config.dbPath)
    val store: StateStore = SqliteStateStore(db)
    val dispatcher = ToolDispatcher()
    val heartbeat = HeartbeatService(store)

    val runner = AgentRunner(
        stateStore = store,
        dispatcher = dispatcher,
        riskScorer = RiskScorer(),
        approvalGate = ApprovalGate(ttlMs = config.approvalTtlMs),
        idempotency = IdempotencyService(),
        heartbeat = heartbeat,
        runtime = AgentRuntime(store),
    )

    // Graceful shutdown
    Runtime.getRuntime().addShutdownHook(Thread({
        log.info("Shutdown hook triggered — closing resources")
        heartbeat.shutdown()
        db.close()
        log.info("AgentShell shut down cleanly")
    }, "agentshell-shutdown"))

    val runConfig = AgentRunner.RunConfig(
        agentId = config.agentId,
        steps = config.steps,
        riskThreshold = config.riskThreshold,
    )

    val outcome = runner.execute(runConfig)
    println("Run finished: runId=${outcome.runId} status=${outcome.status} steps=${outcome.results.size}")
    log.info("Run outcome: runId={} status={} steps={}", outcome.runId, outcome.status, outcome.results.size)

    exitProcess(if (outcome.status.name == "COMPLETED") 0 else 1)
}

private data class CliConfig(
    val agentId: String,
    val dbPath: String,
    val riskThreshold: Int,
    val approvalTtlMs: Long,
    val steps: List<AgentRunner.StepDef>,
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
        System.err.println("Usage: agentshell --agent <id> [--db <path>] [--risk-threshold <0-100>] [--demo]")
        return null
    }

    val dbPath = map["db"] ?: "${System.getProperty("user.home")}/.agentshell/${agentId}.db"
    File(dbPath).parentFile?.mkdirs()

    val riskThreshold = map["risk-threshold"]?.toIntOrNull() ?: 60
    val approvalTtlMs = map["approval-ttl-ms"]?.toLongOrNull() ?: ApprovalGate.DEFAULT_TTL_MS

    // --demo: run a harmless demo step
    val steps: List<AgentRunner.StepDef> = if (map.containsKey("demo")) {
        listOf(
            AgentRunner.StepDef(
                call = ToolCall(callId = "demo-1", toolName = "shell_exec", argumentsJson = """{"command":"echo AgentShell demo OK"}"""),
                contract = ToolContract(name = "shell_exec", version = "1.0", description = "demo shell", riskLevel = RiskLevel.LOW, sandboxPolicy = "shell_default"),
            )
        )
    } else emptyList()

    return CliConfig(agentId, dbPath, riskThreshold, approvalTtlMs, steps)
}

