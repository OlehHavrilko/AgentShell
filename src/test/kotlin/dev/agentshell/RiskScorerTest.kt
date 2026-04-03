package dev.agentshell

import dev.agentshell.runtime.RiskScorer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RiskScorerTest {
    private val scorer = RiskScorer()

    @Test
    fun `force push is high risk`() {
        val score = scorer.score("git_push", "origin main --force")
        assertTrue(score >= 60)
        assertTrue(scorer.requiresApproval(score))
    }

    @Test
    fun `safe file write can be below threshold`() {
        val score = scorer.score("file_write", "update readme")
        assertFalse(score >= 60)
    }
}
