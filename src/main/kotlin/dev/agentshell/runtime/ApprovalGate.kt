package dev.agentshell.runtime

import dev.agentshell.domain.ApprovalRequest
import java.util.concurrent.ConcurrentHashMap

class ApprovalGate {
    private val requests = ConcurrentHashMap<String, ApprovalRequest>()

    fun request(approval: ApprovalRequest): ApprovalRequest {
        requests[approval.approvalId] = approval
        return approval
    }

    fun approve(approvalId: String): ApprovalRequest {
        val current = requests[approvalId] ?: error("approval not found: $approvalId")
        val updated = current.copy(status = "APPROVED")
        requests[approvalId] = updated
        return updated
    }

    fun reject(approvalId: String): ApprovalRequest {
        val current = requests[approvalId] ?: error("approval not found: $approvalId")
        val updated = current.copy(status = "REJECTED")
        requests[approvalId] = updated
        return updated
    }

    fun get(approvalId: String): ApprovalRequest? = requests[approvalId]
}
