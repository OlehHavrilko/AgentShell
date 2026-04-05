package dev.agentshell.observability

import dev.agentshell.state.InMemoryStateStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricsServerTest {

    private lateinit var server: MetricsServer

    @BeforeEach
    fun start() {
        MetricsCollector.reset()
        server = MetricsServer(port = 19091, stateStore = InMemoryStateStore())
        server.start()
        Thread.sleep(100)
    }

    @AfterEach
    fun stop() = server.stop()

    private fun get(path: String): Pair<Int, String> {
        val conn = URL("http://localhost:19091$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 2000
        conn.readTimeout = 2000
        val code = conn.responseCode
        val body = (if (code < 400) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        return code to body
    }

    @Test
    fun `health endpoint returns 200 ok`() {
        val (code, body) = get("/health")
        assertEquals(200, code)
        assertTrue(body.contains("ok"))
    }

    @Test
    fun `metrics endpoint returns prometheus text`() {
        MetricsCollector.runsStarted.set(3)
        val (code, body) = get("/metrics")
        assertEquals(200, code)
        assertTrue(body.contains("agentshell_runs_started_total 3"))
    }

    @Test
    fun `ui endpoint returns HTML dashboard`() {
        val (code, body) = get("/ui")
        assertEquals(200, code)
        assertTrue(body.contains("AgentShell"), "Expected AgentShell in page title")
        assertTrue(body.contains("<html"), "Expected HTML response")
    }

    @Test
    fun `runs endpoint returns JSON`() {
        val (code, body) = get("/runs")
        assertEquals(200, code)
        assertTrue(body.contains("\"runs\""))
    }
}
