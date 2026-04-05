package dev.agentshell.llm.provider.impl

import dev.agentshell.llm.LlmMessage
import dev.agentshell.llm.provider.CompletionRequest
import dev.agentshell.llm.provider.CompletionResponse
import dev.agentshell.llm.provider.ProviderYaml
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient

/**
 * Vertex AI via the predict endpoint.
 * Auth: uses GOOGLE_ACCESS_TOKEN env var (obtain via `gcloud auth print-access-token`
 * or from a service account — no google-auth-library dependency needed).
 */
class VertexAiProvider(http: OkHttpClient, private val cfg: ProviderYaml) : BaseHttpProvider(http) {
    override val id = "vertex_ai"
    override val displayName = "Vertex AI (Gemini)"
    private val project get() = cfg.projectId ?: System.getenv("GOOGLE_CLOUD_PROJECT") ?: ""
    private val location get() = cfg.location ?: "us-central1"
    private val model get() = cfg.model ?: "gemini-1.5-flash"
    private val accessToken get() = System.getenv("GOOGLE_ACCESS_TOKEN") ?: ""
    private val url get() = "https://$location-aiplatform.googleapis.com/v1/projects/$project" +
            "/locations/$location/publishers/google/models/$model:predict"

    override suspend fun complete(request: CompletionRequest): CompletionResponse {
        val contextMessages = request.messages.filter { it.role != LlmMessage.Role.system }
        val sysPrompt = request.systemPrompt
            ?: request.messages.firstOrNull { it.role == LlmMessage.Role.system }?.content

        val messages = buildJsonArray {
            sysPrompt?.let {
                add(buildJsonObject { put("author", "system"); put("content", it) })
            }
            contextMessages.forEach { msg ->
                add(buildJsonObject {
                    put("author", if (msg.role == LlmMessage.Role.assistant) "assistant" else "user")
                    put("content", msg.content ?: "")
                })
            }
        }
        val body = buildJsonObject {
            put("instances", buildJsonArray {
                add(buildJsonObject { put("messages", messages) })
            })
            put("parameters", buildJsonObject {
                put("temperature", request.temperature)
                put("maxOutputTokens", request.maxTokens)
            })
        }
        val resp = post(url, body, mapOf("Authorization" to "Bearer $accessToken"))
        return parseVertexResponse(resp)
    }

    private fun parseVertexResponse(body: String): CompletionResponse {
        val root = json.parseToJsonElement(body).jsonObject
        // predict response: {"predictions": [{"candidates": [{"content": "..."}]}]}
        val prediction = root["predictions"]?.jsonArray?.firstOrNull()?.jsonObject
        val text = prediction?.get("candidates")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
            ?: prediction?.get("content")?.jsonPrimitive?.contentOrNull
        return CompletionResponse(content = text, providerId = id, modelUsed = model)
    }
}
