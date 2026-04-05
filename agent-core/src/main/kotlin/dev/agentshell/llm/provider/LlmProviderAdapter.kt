package dev.agentshell.llm.provider

import dev.agentshell.llm.LlmGateway
import dev.agentshell.llm.LlmMessage
import dev.agentshell.llm.LlmResponse
import dev.agentshell.llm.LlmTool
import kotlinx.coroutines.runBlocking

/** Bridges a new LlmProvider into the existing LlmGateway contract. */
class LlmProviderAdapter(private val provider: LlmProvider) : LlmGateway {
    override fun complete(
        messages: List<LlmMessage>,
        tools: List<LlmTool>,
        systemPrompt: String?,
    ): LlmResponse = runBlocking {
        provider.complete(
            CompletionRequest(
                messages = messages,
                tools = tools,
                systemPrompt = systemPrompt,
            )
        ).toLlmResponse()
    }
}
