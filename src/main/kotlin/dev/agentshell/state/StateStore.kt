package dev.agentshell.state

import dev.agentshell.domain.Checkpoint
import dev.agentshell.domain.Run
import dev.agentshell.domain.RunStatus

interface StateStore {
    fun saveRun(run: Run): Run
    fun findRunByAgent(agentId: String, statuses: Set<RunStatus>): Run?
    fun listRunsByStatus(statuses: Set<RunStatus>): List<Run>
    fun updateHeartbeat(runId: String, heartbeatMs: Long): Run
    fun updateCheckpoint(runId: String, checkpoint: Checkpoint): Run
    fun updateStatus(runId: String, status: RunStatus): Run
}
