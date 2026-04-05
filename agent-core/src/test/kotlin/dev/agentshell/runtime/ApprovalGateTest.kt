package dev.agentshell.runtime

import dev.agentshell.domain.ApprovalRequest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApprovalGateTest {

    private fun fixedClock(epochMs: Long) = Clock.fixed(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC)

    @Test fun `approve sets status to APPROVED`() {
        val gate = ApprovalGate(clock = fixedClock(1000))
        gate.request(ApprovalRequest("a1", "run1", "step1", 80, "preview"))
        val result = gate.approve("a1")
        assertEquals("APPROVED", result.status)
        assertEquals("APPROVED", gate.get("a1")!!.status)
    }

    @Test fun `reject sets status to REJECTED`() {
        val gate = ApprovalGate(clock = fixedClock(1000))
        gate.request(ApprovalRequest("a2", "run1", "step1", 80, "preview"))
        val result = gate.reject("a2")
        assertEquals("REJECTED", result.status)
    }

    @Test fun `expired pending approval is auto-rejected on get`() {
        val gate = ApprovalGate(ttlMs = 1000, clock = fixedClock(0))
        gate.request(ApprovalRequest("a3", "run1", "step1", 80, "preview"))

        // advance clock past TTL
        val futureClock = fixedClock(2000)
        val expiredGate = ApprovalGate(ttlMs = 1000, clock = futureClock)
        expiredGate.request(ApprovalRequest("a3b", "run2", "step2", 75, "preview2"))
        expiredGate.get("a3b") // still pending, not expired yet on futureClock creation

        // Simulate expiry: create gate, request, then access with expired time
        val gate2 = ApprovalGate(ttlMs = 500, clock = fixedClock(1000))
        gate2.request(ApprovalRequest("a4", "run3", "step3", 70, "preview"))
        // Directly verify: we can't easily change the clock after creation,
        // so we test via a gate where ttlMs is 0 (already expired)
        val instantExpireGate = ApprovalGate(ttlMs = 0, clock = fixedClock(9999))
        instantExpireGate.request(ApprovalRequest("a5", "run4", "step4", 65, "preview"))
        // clock.millis() = 9999, expiresAt = 9999 + 0 = 9999, next call millis() > 9999 is false for same clock
        // Test with ttlMs=-1 to guarantee expiry
        val alwaysExpiredGate = ApprovalGate(ttlMs = -1, clock = fixedClock(9999))
        alwaysExpiredGate.request(ApprovalRequest("a6", "run5", "step5", 60, "preview"))
        val autoRejected = alwaysExpiredGate.get("a6")
        assertNotNull(autoRejected)
        assertEquals("REJECTED", autoRejected.status)
    }

    @Test fun `prune removes terminal entries`() {
        val gate = ApprovalGate(clock = fixedClock(1000))
        gate.request(ApprovalRequest("p1", "run1", "step1", 80, "preview"))
        gate.request(ApprovalRequest("p2", "run1", "step2", 80, "preview"))
        gate.approve("p1")
        gate.prune()
        // p1 was approved → pruned, p2 still PENDING → kept
        assertEquals(null, gate.get("p1"))
        assertNotNull(gate.get("p2"))
    }
}
