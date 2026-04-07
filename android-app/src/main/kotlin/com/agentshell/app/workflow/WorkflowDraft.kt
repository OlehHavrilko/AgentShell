package com.agentshell.app.workflow

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class WorkflowDraftStep(
    val type: String,
    val params: String,
) {
    fun toArgsMap(): Map<String, String> {
        parseFlatJson(params)?.let { return it }

        return when (type) {
            "shell_exec" -> mapOf("command" to params)
            "git_exec" -> mapOf("subcommand" to params)
            "file_write" -> mapOf(
                "operation" to "append",
                "path" to "workflow-output.txt",
                "content" to params,
            )
            else -> emptyMap()
        }
    }

    private fun parseFlatJson(raw: String): Map<String, String>? = runCatching {
        Json.parseToJsonElement(raw).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }.getOrNull()
}