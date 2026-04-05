package dev.agentshell.llm.provider.impl

import dev.agentshell.llm.LlmMessage
import dev.agentshell.llm.provider.CompletionRequest
import dev.agentshell.llm.provider.CompletionResponse
import dev.agentshell.llm.provider.ProviderYaml
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient

class CohereProvider(http: OkHttpClient, private val cfg: ProviderYaml) : BaseHttpProvider(http) {
    override val id = "cohere"
    override val displayName = "Cohere"
    private val apiKey get() = cfg.apiKey?.ifBlank { null } ?: System.getenv("COHERE_API_KEY") ?: ""
    private val model get() = cfg.model ?: "command-r-plus"

    override suspend fun complete(request: CompletionRequest): CompletionResponse {
        // Cohere v1 /chat: last user message goes in "message", prior turns in "chat_history"
        val allMessages = buildList {
            request.systemPrompt?.let { add(LlmMessage(LlmMessage.Role.system, it)) }
            addAll(request.messages)
        }
        val lastUserMessage = allMessages.lastOrNull { it.role == LlmMessage.Role.user }?.content ?: ""
        val history = allMessages.dropLast(
            if (allMessages.lastOrNull()?.role == LlmMessage.Role.user) 1 else 0
        )

        val body = buildJsonObject {
            put("model", request.modelOverride ?: model)
            put("message", lastUserMessage)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            if (history.isNotEmpty()) {
                put("chat_history", buildJsonArray {
                    history.forEach { msg ->
                        if (msg.role == LlmMessage.Role.system) return@forEach
                        add(buildJsonObject {
                            // Cohere uses "USER" and "CHATBOT" roles
                            put("role", if (msg.role == LlmMessage.Role.assistant) "CHATBOT" else "USER")
                            put("message", msg.content ?: "")
                        })
                    }
                })
            }
        }
        val resp = post(
            "https://api.cohere.com/v1/chat", body,
            mapOf("Authorization" to "Bearer $apiKey")
        )
        return parseCohereResponse(resp)
    }

    private fun parseCohereResponse(body: String): CompletionResponse {
        val root = json.parseToJsonElement(body).jsonObject
        val text = root["text"]?.jsonPrimitive?.contentOrNull
        val tokens = root["meta"]?.jsonObject?.get("tokens")?.jsonObject
        return CompletionResponse(
            content = text,
            inputTokens = tokens?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: 0,
            outputTokens = tokens?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: 0,
            providerId = id,
            modelUsed = model,
        )
    }
}

