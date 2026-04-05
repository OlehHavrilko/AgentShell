package dev.agentshell.runtime

import dev.agentshell.domain.ApprovalRequest
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

class ApprovalGate(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(ApprovalGate::class.java)
    private val requests = ConcurrentHashMap<String, TimedApproval>()

    fun request(approval: ApprovalRequest): ApprovalRequest {
        requests[approval.approvalId] = TimedApproval(approval, expiresAt = clock.millis() + ttlMs)
        log.info("Approval requested: id={} runId={} riskScore={}", approval.approvalId, approval.runId, approval.riskScore)
        return approval
    }

    fun approve(approvalId: String): ApprovalRequest {
        val timed = requests[approvalId] ?: error("approval not found: $approvalId")
        val updated = timed.approval.copy(status = "APPROVED")
        requests[approvalId] = timed.copy(approval = updated)
        log.info("Approval APPROVED: id={} runId={}", approvalId, updated.runId)
        return updated
    }

    fun reject(approvalId: String): ApprovalRequest {
        val timed = requests[approvalId] ?: error("approval not found: $approvalId")
        val updated = timed.approval.copy(status = "REJECTED")
        requests[approvalId] = timed.copy(approval = updated)
        log.info("Approval REJECTED: id={} runId={}", approvalId, updated.runId)
        return updated
    }

    /**
     * Returns the approval, auto-rejecting it if TTL has expired.
     */
    fun get(approvalId: String): ApprovalRequest? {
        val timed = requests[approvalId] ?: return null
        if (timed.approval.status == "PENDING" && clock.millis() > timed.expiresAt) {
            val expired = timed.approval.copy(status = "REJECTED")
            requests[approvalId] = timed.copy(approval = expired)
            log.warn("Approval EXPIRED and auto-rejected: id={} runId={}", approvalId, expired.runId)
            return expired
        }
        return timed.approval
    }

    /** Returns all PENDING approval requests. */
    fun pendingApprovals(): List<ApprovalRequest> =
        requests.values
            .filter { it.approval.status == "PENDING" && clock.millis() <= it.expiresAt }
            .map { it.approval }

    /** Removes all terminal (non-PENDING) and expired entries. */
    fun prune() {
        val now = clock.millis()
        requests.entries.removeIf { (_, timed) ->
            timed.approval.status != "PENDING" || now > timed.expiresAt
        }
    }

    private data class TimedApproval(val approval: ApprovalRequest, val expiresAt: Long)

    companion object {
        const val DEFAULT_TTL_MS = 3_600_000L // 1 hour
    }
}

