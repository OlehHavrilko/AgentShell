package dev.agentshell.approval

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.agentshell.runtime.ApprovalGate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * Lightweight embedded HTTP server for managing approval requests.
 *
 * Endpoints:
 *   GET  /health               — liveness check
 *   GET  /approvals            — list all PENDING approval requests
 *   POST /approvals/{id}/approve — approve a request
 *   POST /approvals/{id}/reject  — reject a request
 */
class ApprovalServer(
    private val gate: ApprovalGate,
    private val port: Int = 8765,
) {
    private val log = LoggerFactory.getLogger(ApprovalServer::class.java)
    private val json = Json { prettyPrint = true }
    private var server: HttpServer? = null

    fun start() {
        val s = HttpServer.create(InetSocketAddress(port), 0)
        s.createContext("/health") { exchange -> handle(exchange) { _, _ -> ok(exchange, """{"status":"UP"}""") } }
        s.createContext("/approvals") { exchange -> handle(exchange, ::handleApprovals) }
        s.executor = null
        s.start()
        server = s
        log.info("ApprovalServer started on http://localhost:{}", port)
        log.info("  GET  http://localhost:{}/approvals", port)
        log.info("  POST http://localhost:{}/approvals/{{id}}/approve", port)
        log.info("  POST http://localhost:{}/approvals/{{id}}/reject", port)
    }

    fun stop() {
        server?.stop(0)
        server = null
        log.info("ApprovalServer stopped")
    }

    private fun handle(exchange: HttpExchange, block: (HttpExchange, List<String>) -> Unit) {
        try {
            val segments = exchange.requestURI.path.trim('/').split('/')
            block(exchange, segments)
        } catch (e: Exception) {
            log.error("ApprovalServer error: {}", e.message, e)
            error(exchange, 500, e.message ?: "internal error")
        }
    }

    private fun handleApprovals(exchange: HttpExchange, segments: List<String>) {
        // GET /approvals
        if (exchange.requestMethod == "GET" && segments.size == 1) {
            val pending = gate.pendingApprovals()
            ok(exchange, json.encodeToString(pending.map { mapOf(
                "approvalId" to it.approvalId,
                "runId" to it.runId,
                "stepId" to it.stepId,
                "riskScore" to it.riskScore.toString(),
                "impactPreview" to it.impactPreview,
                "status" to it.status,
            )}))
            return
        }
        // POST /approvals/{id}/approve|reject
        if (exchange.requestMethod == "POST" && segments.size == 3) {
            val id = segments[1]
            when (segments[2]) {
                "approve" -> {
                    val result = gate.approve(id)
                    ok(exchange, """{"approvalId":"$id","status":"${result.status}"}""")
                }
                "reject" -> {
                    val result = gate.reject(id)
                    ok(exchange, """{"approvalId":"$id","status":"${result.status}"}""")
                }
                else -> error(exchange, 404, "unknown action: ${segments[2]}")
            }
            return
        }
        error(exchange, 405, "method not allowed")
    }

    private fun ok(exchange: HttpExchange, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun error(exchange: HttpExchange, code: Int, message: String) {
        val escaped = message.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        val body = """{"error":"$escaped"}""".toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(code, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }
}
