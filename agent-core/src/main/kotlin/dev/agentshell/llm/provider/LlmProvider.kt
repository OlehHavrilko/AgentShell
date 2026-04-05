package dev.agentshell.llm.provider

import dev.agentshell.llm.LlmMessage
import dev.agentshell.llm.LlmResponse
import dev.agentshell.llm.LlmTool
import dev.agentshell.llm.LlmToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class CompletionRequest(
    val messages: List<LlmMessage>,
    val tools: List<LlmTool> = emptyList(),
    val systemPrompt: String? = null,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
    val modelOverride: String? = null,
)

data class CompletionResponse(
    val content: String?,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val stopReason: LlmResponse.StopReason = LlmResponse.StopReason.END_TURN,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val providerId: String = "",
    val modelUsed: String = "",
) {
    fun toLlmResponse() = LlmResponse(
        content = content,
        toolCalls = toolCalls,
        stopReason = stopReason,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
    )
}

data class CompletionChunk(val delta: String, val done: Boolean = false)

interface LlmProvider {
    val id: String
    val displayName: String
    val isLocal: Boolean get() = false

    suspend fun complete(request: CompletionRequest): CompletionResponse

    fun stream(request: CompletionRequest): Flow<CompletionChunk> = emptyFlow()
}
