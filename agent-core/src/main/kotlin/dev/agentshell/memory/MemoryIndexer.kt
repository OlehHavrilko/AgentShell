package dev.agentshell.memory

import dev.agentshell.audit.AuditEvent
import dev.agentshell.audit.AuditEventType
import dev.agentshell.audit.AuditTrail
import dev.agentshell.state.StateStore
import java.util.UUID

/**
 * Auto-indexes completed runs into MemoryStore by scanning AuditTrail events.
 *
 * Call indexRun(runId) after a run completes. Extracts step-level tool activity
 * and stores concise summaries so future agents can retrieve relevant past context.
 */
class MemoryIndexer(
    private val memoryStore: MemoryStore,
    private val auditTrail: AuditTrail,
    private val stateStore: StateStore
) {

    fun indexRun(runId: String) {
        val run = stateStore.getRun(runId) ?: return
        val events = auditTrail.queryByRun(runId)

        val stepEvents = events.filter {
            it.eventType == AuditEventType.STEP_COMPLETED || it.eventType == AuditEventType.STEP_FAILED
        }

        for (event in stepEvents) {
            val text = buildText(run.agentId, event)
            val entry = MemoryEntry(
                id = UUID.randomUUID().toString(),
                runId = runId,
                agentId = run.agentId,
                text = text,
                tags = listOfNotNull(event.toolName, run.agentId, event.eventType.name.lowercase())
            )
            memoryStore.store(entry)
        }

        // Run-level summary entry
        val runSummary = MemoryEntry(
            id = UUID.randomUUID().toString(),
            runId = runId,
            agentId = run.agentId,
            text = "run=${runId.take(8)} agent=${run.agentId} status=${run.status} steps=${stepEvents.size}",
            tags = listOf("run-summary", run.agentId, run.status.name.lowercase())
        )
        memoryStore.store(runSummary)
    }

    private fun buildText(agentId: String, event: AuditEvent): String {
        val tool = event.toolName ?: "unknown"
        val args = event.argsJson?.take(120) ?: ""
        val result = event.resultJson?.take(120) ?: ""
        return "agent=$agentId tool=$tool args=$args result=$result"
    }
}
