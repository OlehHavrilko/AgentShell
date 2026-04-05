package dev.agentshell.state

import dev.agentshell.domain.Checkpoint
import dev.agentshell.domain.Run
import dev.agentshell.domain.RunStatus
import java.util.concurrent.ConcurrentHashMap

class InMemoryStateStore : StateStore {
    private val runs = ConcurrentHashMap<String, Run>()

    override fun saveRun(run: Run): Run {
        runs[run.runId] = run
        return run
    }

    override fun findRunByAgent(agentId: String, statuses: Set<RunStatus>): Run? {
        return runs.values
            .filter { it.agentId == agentId && statuses.contains(it.status) }
            .maxByOrNull { it.heartbeatMs }
    }

    override fun updateHeartbeat(runId: String, heartbeatMs: Long): Run {
        val current = runs[runId] ?: error("run not found: $runId")
        val updated = current.copy(heartbeatMs = heartbeatMs)
        runs[runId] = updated
        return updated
    }

    override fun updateCheckpoint(runId: String, checkpoint: Checkpoint): Run {
        val current = runs[runId] ?: error("run not found: $runId")
        val updated = current.copy(checkpoint = checkpoint)
        runs[runId] = updated
        return updated
    }

    override fun updateStatus(runId: String, status: RunStatus): Run {
        val current = runs[runId] ?: error("run not found: $runId")
        val updated = current.copy(status = status)
        runs[runId] = updated
        return updated
    }
}
