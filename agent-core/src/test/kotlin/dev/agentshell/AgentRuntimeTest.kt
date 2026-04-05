package dev.agentshell

import dev.agentshell.domain.Checkpoint
import dev.agentshell.domain.Run
import dev.agentshell.domain.RunStatus
import dev.agentshell.runtime.AgentRuntime
import dev.agentshell.runtime.RuntimeDecision
import dev.agentshell.state.InMemoryStateStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentRuntimeTest {
    @Test
    fun `start creates new run`() {
        val store = InMemoryStateStore()
        val runtime = AgentRuntime(store)

        val result = runtime.startOrResume("agent-1")
        assertTrue(result is RuntimeDecision.StartNew)
        assertEquals(0, result.fromStep)
    }

    @Test
    fun `resume uses checkpoint next step`() {
        val store = InMemoryStateStore()
        store.saveRun(
            Run(
                runId = "run-1",
                agentId = "agent-2",
                status = RunStatus.CRASHED,
                heartbeatMs = 1,
                checkpoint = Checkpoint(stepIndex = 3, contextSummary = "ok", artifactRefs = emptyList()),
            ),
        )
        val runtime = AgentRuntime(store)

        val result = runtime.startOrResume("agent-2")
        assertTrue(result is RuntimeDecision.Resume)
        assertEquals(4, result.fromStep)
    }
}
