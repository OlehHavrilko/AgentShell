package dev.agentshell.llm.provider.impl

import dev.agentshell.llm.LlmResponse
import dev.agentshell.llm.LlmToolCall
import dev.agentshell.llm.provider.CompletionRequest
import dev.agentshell.llm.provider.CompletionResponse
import dev.agentshell.llm.provider.ProviderYaml
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient

class OllamaProvider(http: OkHttpClient, private val cfg: ProviderYaml) : BaseHttpProvider(http) {
    override val id = "ollama"
    override val displayName = "Ollama (Local)"
    override val isLocal = true
    private val model get() = cfg.model ?: "llama3.2"
    private val baseUrl get() = cfg.host?.trimEnd('/') ?: "http://localhost:11434"

    override suspend fun complete(request: CompletionRequest): CompletionResponse {
        val body = buildJsonObject {
            put("model", request.modelOverride ?: model)
            put("stream", false)
            put("options", buildJsonObject {
                put("temperature", request.temperature)
                put("num_predict", request.maxTokens)
            })
            put("messages", buildMessagesArray(request.messages, request.systemPrompt))
            if (request.tools.isNotEmpty()) put("tools", buildToolsArray(request.tools))
        }
        val resp = post("$baseUrl/api/chat", body, emptyMap())
        return parseOllamaResponse(resp)
    }

    private fun parseOllamaResponse(body: String): CompletionResponse {
        val root = json.parseToJsonElement(body).jsonObject
        val message = root["message"]?.jsonObject
        val content = message?.get("content")?.jsonPrimitive?.contentOrNull
        val toolCalls = message?.get("tool_calls")?.jsonArray?.map { tc ->
            val fn = tc.jsonObject["function"]?.jsonObject
            LlmToolCall(
                id = System.nanoTime().toString(),
                toolName = fn?.get("name")?.jsonPrimitive?.content ?: "",
                argumentsJson = fn?.get("arguments")?.let { json.encodeToString(JsonElement.serializer(), it) } ?: "{}",
            )
        } ?: emptyList()
        val prompt = root["prompt_eval_count"]?.jsonPrimitive?.intOrNull ?: 0
        val eval = root["eval_count"]?.jsonPrimitive?.intOrNull ?: 0
        return CompletionResponse(
            content = content,
            toolCalls = toolCalls,
            stopReason = if (toolCalls.isNotEmpty()) LlmResponse.StopReason.TOOL_USE else LlmResponse.StopReason.END_TURN,
            inputTokens = prompt,
            outputTokens = eval,
            providerId = id,
            modelUsed = model,
        )
    }
}
