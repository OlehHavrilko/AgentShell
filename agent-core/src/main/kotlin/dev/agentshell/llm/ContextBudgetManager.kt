package dev.agentshell.llm

/**
 * Tracks token usage for a conversation and manages context window limits.
 *
 * Uses a simple character-to-token approximation (4 chars ≈ 1 token) for
 * providers that don't return exact counts until after the call.
 */
class ContextBudgetManager(
    private val maxInputTokens: Int = 100_000,
    private val reserveForOutput: Int = 4_000,
) {
    private var inputTokensUsed: Int = 0
    private var outputTokensUsed: Int = 0

    val totalInputTokens: Int
        get() = inputTokensUsed

    val totalOutputTokens: Int
        get() = outputTokensUsed

    val availableInputTokens: Int
        get() = (maxInputTokens - reserveForOutput - inputTokensUsed).coerceAtLeast(0)

    fun recordUsage(inputTokens: Int, outputTokens: Int) {
        inputTokensUsed += inputTokens
        outputTokensUsed += outputTokens
    }

    fun reset() {
        inputTokensUsed = 0
        outputTokensUsed = 0
    }

    fun isNearLimit(): Boolean = availableInputTokens < reserveForOutput

    /**
     * Trims the oldest non-system messages from the conversation until
     * the estimated token count fits within the budget.
     * Always preserves the system message (index 0 if role == system).
     */
    fun trimToFit(messages: List<LlmMessage>): List<LlmMessage> {
        if (!isNearLimit()) return messages

        val systemMessages = messages.filter { it.role == LlmMessage.Role.system }
        val nonSystem = messages.filter { it.role != LlmMessage.Role.system }.toMutableList()

        var estimatedTokens = estimateTokens(messages)
        while (estimatedTokens > maxInputTokens - reserveForOutput && nonSystem.size > 1) {
            nonSystem.removeAt(0)
            estimatedTokens = estimateTokens(systemMessages + nonSystem)
        }

        return systemMessages + nonSystem
    }

    private fun estimateTokens(messages: List<LlmMessage>): Int =
        messages.sumOf { msg ->
            val contentLen = msg.content?.length ?: 0
            val toolCallLen = msg.toolCalls.sumOf { it.argumentsJson.length + it.toolName.length }
            (contentLen + toolCallLen) / 4 + 4 // 4 tokens overhead per message
        }

    override fun toString(): String =
        "ContextBudget(used=$inputTokensUsed/$maxInputTokens out=$outputTokensUsed)"
}
