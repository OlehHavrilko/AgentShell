package dev.agentshell.observability

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.agentshell.report.RunReportGenerator
import dev.agentshell.state.StateStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Lightweight embedded HTTP server exposing observability endpoints.
 *
 * Routes:
 *   GET /ui                   — Web dashboard (HTML)
 *   GET /runs                 — JSON list of all runs
 *   GET /report/{runId}       — JSON run report (RunReportGenerator)
 *   GET /metrics              — Prometheus text format (MetricsCollector)
 *   GET /health               — {"status":"ok"}
 *
 * Start with `--metrics-port <port>`.
 */
class MetricsServer(
    private val port: Int,
    private val reportGenerator: RunReportGenerator? = null,
    private val stateStore: StateStore? = null,
    private val memoryStore: dev.agentshell.memory.MemoryStore? = null,
) {
    private val log = LoggerFactory.getLogger(MetricsServer::class.java)
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    private var server: HttpServer? = null

    fun start() {
        val s = HttpServer.create(InetSocketAddress(port), 0)
        s.executor = Executors.newFixedThreadPool(4) { r ->
            Thread(r, "agentshell-metrics-http").also { it.isDaemon = true }
        }

        // ── Static UI ──────────────────────────────────────────────────────
        s.createContext("/ui") { ex ->
            if (ex.requestMethod != "GET") { ex.respond(405, "text/plain", "Method Not Allowed"); return@createContext }
            val html = MetricsServer::class.java.classLoader
                .getResourceAsStream("ui/index.html")
                ?.bufferedReader()?.readText()
                ?: "<h1>UI not found</h1>"
            ex.respond(200, "text/html; charset=utf-8", html)
        }

        // ── Runs list ──────────────────────────────────────────────────────
        s.createContext("/runs") { ex ->
            if (ex.requestMethod != "GET") { ex.respond(405, "text/plain", "Method Not Allowed"); return@createContext }
            val runs = stateStore?.listAll() ?: emptyList()
            val items = runs.sortedByDescending { it.heartbeatMs }.map { run ->
                // Try to get timing from report if available
                val report = try { reportGenerator?.generate(run.runId) } catch (_: Exception) { null }
                buildString {
                    append("""{"runId":"${run.runId}","agentId":"${run.agentId}","status":"${run.status}"""")
                    append(""","startedAtMs":${report?.startedAtMs ?: "null"}""")
                    append(""","durationMs":${report?.durationMs ?: "null"}""")
                    append("}")
                }
            }
            ex.respond(200, "application/json", """{"runs":[${items.joinToString(",")}]}""")
        }

        // ── Health ─────────────────────────────────────────────────────────
        s.createContext("/health") { ex ->
            ex.respond(200, "application/json", """{"status":"ok"}""")
        }

        // ── Metrics ────────────────────────────────────────────────────────
        s.createContext("/metrics") { ex ->
            if (ex.requestMethod != "GET") { ex.respond(405, "text/plain", "Method Not Allowed"); return@createContext }
            ex.respond(200, "text/plain; version=0.0.4; charset=utf-8", MetricsCollector.toPrometheusText())
        }

        // ── Run report ─────────────────────────────────────────────────────
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
                ex.respond(200, "application/json", reportGenerator.generateJson(runId))
            } catch (e: IllegalStateException) {
                ex.respond(404, "application/json", """{"error":"${e.message}"}""")
            }
        }

        // ── Memory REST API ────────────────────────────────────────────────
        s.createContext("/memory") { ex ->
            val path = ex.requestURI.path.removePrefix("/memory").trimStart('/')
            val query = ex.requestURI.rawQuery
                ?.split("&")
                ?.associate { p -> p.split("=", limit = 2).let { it[0] to (it.getOrNull(1) ?: "") } }
                ?: emptyMap()

            when {
                ex.requestMethod == "GET" && path == "search" -> {
                    val q = query["query"]?.trim() ?: ""
                    val topK = query["topK"]?.toIntOrNull() ?: 10
                    val tag = query["tag"]?.trim()
                    if (memoryStore == null) {
                        ex.respond(503, "application/json", """{"error":"memory store not configured"}""")
                        return@createContext
                    }
                    val results = if (!tag.isNullOrBlank()) memoryStore.searchByTag(tag, topK)
                    else if (q.isNotBlank()) memoryStore.search(q, topK)
                    else memoryStore.getAll().takeLast(topK)
                    val items = results.joinToString(",") { e ->
                        """{"id":"${e.id}","runId":"${e.runId}","agentId":"${e.agentId}","text":${
                            json.encodeToString(e.text)
                        },"tags":${json.encodeToString(e.tags)},"score":${e.score},"createdAtMs":${e.createdAt.toEpochMilli()}}"""
                    }
                    ex.respond(200, "application/json", """{"results":[$items],"total":${results.size}}""")
                }
                ex.requestMethod == "GET" && path == "entries" -> {
                    val all = memoryStore?.getAll() ?: emptyList()
                    val items = all.joinToString(",") { e ->
                        """{"id":"${e.id}","runId":"${e.runId}","agentId":"${e.agentId}","tags":${
                            json.encodeToString(e.tags)
                        },"createdAtMs":${e.createdAt.toEpochMilli()}}"""
                    }
                    ex.respond(200, "application/json", """{"entries":[$items],"total":${all.size}}""")
                }
                ex.requestMethod == "POST" && path == "store" -> {
                    if (memoryStore == null) {
                        ex.respond(503, "application/json", """{"error":"memory store not configured"}""")
                        return@createContext
                    }
                    val body = ex.requestBody.bufferedReader().readText()
                    val obj = json.parseToJsonElement(body).jsonObject
                    val entry = dev.agentshell.memory.MemoryEntry(
                        id = obj["id"]?.jsonPrimitive?.content ?: java.util.UUID.randomUUID().toString(),
                        runId = obj["runId"]?.jsonPrimitive?.content ?: "manual",
                        agentId = obj["agentId"]?.jsonPrimitive?.content ?: "manual",
                        text = obj["text"]?.jsonPrimitive?.content ?: "",
                        tags = obj["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        decayHalfLifeMs = obj["decayHalfLifeMs"]?.jsonPrimitive?.long ?: Long.MAX_VALUE,
                    )
                    memoryStore.store(entry)
                    ex.respond(201, "application/json", """{"id":"${entry.id}"}""")
                }
                ex.requestMethod == "DELETE" && path == "clear" -> {
                    memoryStore?.clear()
                    ex.respond(200, "application/json", """{"ok":true}""")
                }
                else -> ex.respond(404, "application/json", """{"error":"not found — try GET /memory/search?query=... or GET /memory/entries"}""")
            }
        }

        s.start()
        server = s
        log.info("MetricsServer started on :{} — UI: http://localhost:{}/ui", port, port)
    }

    fun stop() {
        server?.stop(0)
        server = null
        log.info("MetricsServer stopped")
    }

    private fun HttpExchange.respond(code: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        responseHeaders.set("Content-Type", contentType)
        responseHeaders.set("Access-Control-Allow-Origin", "*")
        sendResponseHeaders(code, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
