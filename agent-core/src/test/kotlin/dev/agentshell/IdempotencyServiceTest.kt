package dev.agentshell

import dev.agentshell.runtime.IdempotencyService
import dev.agentshell.runtime.IdempotencyStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class IdempotencyServiceTest {
    @Test
    fun `same key is not duplicated`() {
        val svc = IdempotencyService()
        val first = svc.begin("k1")
        val second = svc.begin("k1")

        assertEquals(IdempotencyStatus.IN_PROGRESS, first.status)
        assertEquals(first, second)
    }

    @Test
    fun `completed key returns completed status`() {
        val svc = IdempotencyService()
        svc.begin("k2")
        svc.complete("k2", "{\"ok\":true}")

        assertEquals(IdempotencyStatus.COMPLETED, svc.get("k2")?.status)
    }
}
