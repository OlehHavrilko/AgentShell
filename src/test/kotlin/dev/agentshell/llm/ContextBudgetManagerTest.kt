package dev.agentshell.llm

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextBudgetManagerTest {

    @Test
    fun `starts with full budget`() {
        val budget = ContextBudgetManager(maxInputTokens = 10_000, reserveForOutput = 1_000)
        assertEquals(9_000, budget.availableInputTokens)
        assertFalse(budget.isNearLimit())
    }

    @Test
    fun `recordUsage reduces available tokens`() {
        val budget = ContextBudgetManager(maxInputTokens = 10_000, reserveForOutput = 1_000)
        budget.recordUsage(inputTokens = 8_500, outputTokens = 500)
        // available = 10000 - 1000 - 8500 = 500, which is < reserveForOutput (1000)
        assertEquals(500, budget.availableInputTokens)
        assertTrue(budget.isNearLimit())
    }

    @Test
    fun `reset restores full budget`() {
        val budget = ContextBudgetManager(maxInputTokens = 5_000, reserveForOutput = 500)
        budget.recordUsage(4_000, 200)
        budget.reset()
        assertEquals(4_500, budget.availableInputTokens)
    }

    @Test
    fun `trimToFit removes oldest non-system messages`() {
        val budget = ContextBudgetManager(maxInputTokens = 100, reserveForOutput = 50)
        // Make each message ~25 tokens (100 chars ÷ 4)
        val systemMsg = LlmMessage(LlmMessage.Role.system, "system prompt")
        val old1 = LlmMessage(LlmMessage.Role.user, "a".repeat(100))
        val old2 = LlmMessage(LlmMessage.Role.assistant, "b".repeat(100))
        val recent = LlmMessage(LlmMessage.Role.user, "recent message")

        budget.recordUsage(inputTokens = 90, outputTokens = 0) // near limit

        val messages = listOf(systemMsg, old1, old2, recent)
        val trimmed = budget.trimToFit(messages)

        // System message must be preserved
        assertTrue(trimmed.any { it.role == LlmMessage.Role.system })
        // Recent message should be present
        assertTrue(trimmed.any { it.content == "recent message" })
    }

    @Test
    fun `trimToFit returns same list when not near limit`() {
        val budget = ContextBudgetManager(maxInputTokens = 100_000, reserveForOutput = 1_000)
        val messages = listOf(
            LlmMessage(LlmMessage.Role.system, "sys"),
            LlmMessage(LlmMessage.Role.user, "user"),
        )
        val result = budget.trimToFit(messages)
        assertEquals(messages, result)
    }

    @Test
    fun `availableInputTokens never goes negative`() {
        val budget = ContextBudgetManager(maxInputTokens = 1_000, reserveForOutput = 500)
        budget.recordUsage(inputTokens = 2_000, outputTokens = 0)
        assertEquals(0, budget.availableInputTokens)
    }
}
