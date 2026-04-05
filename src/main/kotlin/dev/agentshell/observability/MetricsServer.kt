package dev.agentshell.observability

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.agentshell.report.RunReportGenerator
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Lightweight embedded HTTP server exposing observability endpoints.
 *
 * Routes:
 *   GET /metrics              — Prometheus text format (MetricsCollector)
 *   GET /report/{runId}       — JSON run report (RunReportGenerator)
 *   GET /health               — {"status":"ok"}
 *
 * Start with `--metrics-port <port>`. Uses the same JDK HttpServer
 * approach as ApprovalServer (zero extra dependencies).
 */
class MetricsServer(
    private val port: Int,
    private val reportGenerator: RunReportGenerator? = null,
) {
    private val log = LoggerFactory.getLogger(MetricsServer::class.java)
    private var server: HttpServer? = null

    fun start() {
        val s = HttpServer.create(InetSocketAddress(port), 0)
        s.executor = Executors.newFixedThreadPool(2) { r ->
            Thread(r, "agentshell-metrics-http").also { it.isDaemon = true }
        }

        s.createContext("/health") { ex ->
            ex.respond(200, "application/json", """{"status":"ok"}""")
        }

        s.createContext("/metrics") { ex ->
            if (ex.requestMethod != "GET") { ex.respond(405, "text/plain", "Method Not Allowed"); return@createContext }
            ex.respond(200, "text/plain; version=0.0.4; charset=utf-8", MetricsCollector.toPrometheusText())
        }

        s.createContext("/report") { ex ->
            if (ex.requestMethod != "GET") { ex.respond(405, "text/plain", "Method Not Allowed"); return@createContext }
            val runId = ex.requestURI.path.removePrefix("/report").trimStart('/')
            if (runId.isBlank()) {
                ex.respond(400, "application/json", """{"error":"runId required — GET /report/{runId}"}""")
                return@createContext
            }
            if (reportGenerator == null) {
                ex.respond(503, "application/json", """{"error":"report generator not configured"}""")
                return@createContext
            }
            try {
                val body = reportGenerator.generateJson(runId)
                ex.respond(200, "application/json", body)
            } catch (e: IllegalStateException) {
                ex.respond(404, "application/json", """{"error":"${e.message}"}""")
            }
        }

        s.start()
        server = s
        log.info("MetricsServer started on :{}", port)
    }

    fun stop() {
        server?.stop(0)
        server = null
        log.info("MetricsServer stopped")
    }

    private fun HttpExchange.respond(code: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        responseHeaders.set("Content-Type", contentType)
        sendResponseHeaders(code, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
