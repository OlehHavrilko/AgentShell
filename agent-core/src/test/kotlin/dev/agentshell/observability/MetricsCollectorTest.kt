package dev.agentshell.observability

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricsCollectorTest {

    @BeforeEach @AfterEach
    fun resetMetrics() = MetricsCollector.reset()

    @Test
    fun `counters start at zero after reset`() {
        assertEquals(0, MetricsCollector.runsStarted.get())
        assertEquals(0, MetricsCollector.runsCompleted.get())
        assertEquals(0, MetricsCollector.tokensInput.get())
    }

    @Test
    fun `increment counters`() {
        MetricsCollector.runsStarted.incrementAndGet()
        MetricsCollector.runsStarted.incrementAndGet()
        MetricsCollector.runsCompleted.incrementAndGet()
        assertEquals(2, MetricsCollector.runsStarted.get())
        assertEquals(1, MetricsCollector.runsCompleted.get())
    }

    @Test
    fun `recordTokens accumulates correctly`() {
        MetricsCollector.recordTokens(100, 50)
        MetricsCollector.recordTokens(200, 75)
        assertEquals(300, MetricsCollector.tokensInput.get())
        assertEquals(125, MetricsCollector.tokensOutput.get())
    }

    @Test
    fun `toPrometheusText contains all metric names`() {
        val text = MetricsCollector.toPrometheusText()
        listOf(
            "agentshell_runs_started_total",
            "agentshell_runs_completed_total",
            "agentshell_runs_failed_total",
            "agentshell_approvals_requested_total",
            "agentshell_tool_calls_total",
            "agentshell_tokens_input_total",
            "agentshell_tokens_output_total",
            "agentshell_watchdog_crashes_total",
        ).forEach { name ->
            assertTrue(text.contains(name), "Missing metric: $name")
        }
    }

    @Test
    fun `toPrometheusText reflects incremented values`() {
        MetricsCollector.runsStarted.set(7)
        MetricsCollector.runsFailed.set(2)
        val text = MetricsCollector.toPrometheusText()
        assertTrue(text.contains("agentshell_runs_started_total 7"))
        assertTrue(text.contains("agentshell_runs_failed_total 2"))
    }
}
