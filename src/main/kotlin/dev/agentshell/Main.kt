package dev.agentshell

import dev.agentshell.approval.ApprovalServer
import dev.agentshell.audit.SqliteAuditTrail
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
import dev.agentshell.workflow.WorkflowLoader
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger("dev.agentshell.Main")

fun main(args: Array<String>) {
    val config = parseArgs(args) ?: exitProcess(1)

    log.info("AgentShell starting — agentId={} db={}", config.agentId, config.dbPath)
    println("AgentShell v2.0 | agentId=${config.agentId} | db=${config.dbPath}")

    val db = DatabaseManager(config.dbPath)
    val store: StateStore = SqliteStateStore(db)
    val auditTrail = SqliteAuditTrail(db)
    val dispatcher = ToolDispatcher()
    val heartbeat = HeartbeatService(store)
    val approvalGate = ApprovalGate(ttlMs = config.approvalTtlMs)

    // Optional approval HTTP server
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
    )

    Runtime.getRuntime().addShutdownHook(Thread({
        log.info("Shutdown hook triggered — closing resources")
        approvalServer?.stop()
        heartbeat.shutdown()
        db.close()
        log.info("AgentShell shut down cleanly")
    }, "agentshell-shutdown"))

    val outcome = runner.execute(config.runConfig)
    println("Run finished: runId=${outcome.runId} status=${outcome.status} steps=${outcome.results.size}")
    log.info("Run outcome: runId={} status={} steps={}", outcome.runId, outcome.status, outcome.results.size)

    exitProcess(if (outcome.status.name == "COMPLETED") 0 else 1)
}

private data class CliConfig(
    val agentId: String,
    val dbPath: String,
    val approvalTtlMs: Long,
    val approvalPort: Int?,
    val runConfig: AgentRunner.RunConfig,
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
            Options:
              --db <path>               SQLite database path (default: ~/.agentshell/<id>.db)
              --workflow <file>         Load steps from .yaml/.yml/.json workflow file
              --risk-threshold <0-100>  Approval threshold (default: 60)
              --approval-ttl-ms <ms>    Approval TTL (default: 3600000)
              --approval-port <port>    Start approval HTTP server on given port
              --demo                    Run built-in demo workflow
        """.trimIndent())
        return null
    }

    val dbPath = map["db"] ?: "${System.getProperty("user.home")}/.agentshell/${agentId}.db"
    File(dbPath).parentFile?.mkdirs()

    val riskThreshold = map["risk-threshold"]?.toIntOrNull() ?: 60
    val approvalTtlMs = map["approval-ttl-ms"]?.toLongOrNull() ?: ApprovalGate.DEFAULT_TTL_MS
    val approvalPort = map["approval-port"]?.toIntOrNull()

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

    return CliConfig(agentId, dbPath, approvalTtlMs, approvalPort, runConfig)
}
