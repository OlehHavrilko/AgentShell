package dev.agentshell.llm.provider.impl

import dev.agentshell.llm.LlmMessage
import dev.agentshell.llm.provider.CompletionRequest
import dev.agentshell.llm.provider.CompletionResponse
import dev.agentshell.llm.provider.ProviderYaml
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient

/** Vertex AI via Gemini REST endpoint (no Google Auth library — uses API key or access token from env) */
class VertexAiProvider(http: OkHttpClient, private val cfg: ProviderYaml) : BaseHttpProvider(http) {
    override val id = "vertex_ai"
    override val displayName = "Vertex AI (Gemini)"
    private val project get() = cfg.projectId ?: System.getenv("GOOGLE_CLOUD_PROJECT") ?: ""
    private val location get() = cfg.location ?: "us-central1"
    private val model get() = cfg.model ?: "gemini-1.5-flash"
    private val accessToken get() = System.getenv("GOOGLE_ACCESS_TOKEN") ?: ""
    private val url get() = "https://$location-aiplatform.googleapis.com/v1/projects/$project/locations/$location/publishers/google/models/$model:generateContent"

    override suspend fun complete(request: CompletionRequest): CompletionResponse {
        val contents = buildJsonArray {
            request.messages.filter { it.role != LlmMessage.Role.system }.forEach { msg ->
                add(buildJsonObject {
                    put("role", if (msg.role == LlmMessage.Role.assistant) "model" else "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", msg.content ?: "") }) })
                })
            }
        }
        val body = buildJsonObject {
            put("contents", contents)
            request.systemPrompt?.let {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", it) }) })
                })
            }
        }
        val resp = post(url, body, mapOf("Authorization" to "Bearer $accessToken"))
        return parseVertexResponse(resp)
    }

    private fun parseVertexResponse(body: String): CompletionResponse {
        val root = json.parseToJsonElement(body).jsonObject
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        val content = candidate?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
        return CompletionResponse(content = content, providerId = id, modelUsed = model)
    }
}
