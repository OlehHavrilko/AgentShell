package dev.agentshell.runtime

import dev.agentshell.domain.Checkpoint
import dev.agentshell.domain.Run
import dev.agentshell.domain.RunStatus
import dev.agentshell.state.StateStore
import java.time.Clock
import java.util.UUID

class AgentRuntime(
    private val stateStore: StateStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun startOrResume(agentId: String): RuntimeDecision {
        val existing = stateStore.findRunByAgent(
            agentId = agentId,
            statuses = setOf(RunStatus.RUNNING, RunStatus.CRASHED, RunStatus.WAITING_APPROVAL),
        )

        if (existing == null) {
            val run = Run(
                runId = UUID.randomUUID().toString(),
                agentId = agentId,
                status = RunStatus.RUNNING,
                heartbeatMs = clock.millis(),
                checkpoint = Checkpoint(stepIndex = -1, contextSummary = "", artifactRefs = emptyList()),
            )
            stateStore.saveRun(run)
            return RuntimeDecision.StartNew(run.runId, fromStep = 0)
        }

        if (existing.status == RunStatus.WAITING_APPROVAL) {
            return RuntimeDecision.WaitingApproval(existing.runId)
        }

        val stepIndex = existing.checkpoint?.stepIndex ?: -1
        stateStore.updateStatus(existing.runId, RunStatus.RUNNING)
        stateStore.updateHeartbeat(existing.runId, clock.millis())
        return RuntimeDecision.Resume(existing.runId, fromStep = stepIndex + 1)
    }
}

sealed interface RuntimeDecision {
    data class StartNew(val runId: String, val fromStep: Int) : RuntimeDecision
    data class Resume(val runId: String, val fromStep: Int) : RuntimeDecision
    data class WaitingApproval(val runId: String) : RuntimeDecision
}
