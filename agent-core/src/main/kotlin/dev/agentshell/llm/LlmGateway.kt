package dev.agentshell.llm

import kotlinx.serialization.Serializable

/** A single message in an LLM conversation. */
@Serializable
data class LlmMessage(
    val role: Role,
    val content: String? = null,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolCallId: String? = null,
) {
    enum class Role { system, user, assistant, tool }
}

/** A tool invocation decision made by the LLM. */
@Serializable
data class LlmToolCall(
    val id: String,
    val toolName: String,
    val argumentsJson: String,
)

/** A tool available for the LLM to call. */
@Serializable
data class LlmTool(
    val name: String,
    val description: String,
    val parametersJson: String,
)

/** The response from an LLM completion call. */
@Serializable
data class LlmResponse(
    val content: String?,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val stopReason: StopReason,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
) {
    enum class StopReason { TOOL_USE, END_TURN, MAX_TOKENS, ERROR }
}

/** Contract for an LLM provider. */
interface LlmGateway {
    /**
     * Send conversation to the LLM and get back the next response.
     * @param messages The full conversation history so far.
     * @param tools Tools available to the LLM (may be empty).
     * @param systemPrompt Optional system prompt prepended to messages.
     */
    fun complete(
        messages: List<LlmMessage>,
        tools: List<LlmTool> = emptyList(),
        systemPrompt: String? = null,
    ): LlmResponse
}
