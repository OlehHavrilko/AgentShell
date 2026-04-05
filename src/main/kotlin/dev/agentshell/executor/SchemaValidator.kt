package dev.agentshell.executor

import dev.agentshell.domain.ErrorCode
import dev.agentshell.domain.ToolCall
import dev.agentshell.domain.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Validates that a ToolCall's argumentsJson is well-formed JSON before execution.
 */
object SchemaValidator {

    private val json = Json { ignoreUnknownKeys = true }

    fun validate(call: ToolCall): ToolResult? {
        if (call.argumentsJson.isBlank()) {
            return ToolResult(
                callId = call.callId,
                success = false,
                errorCode = ErrorCode.ERR_SCHEMA_VIOLATION,
                errorDetail = "argumentsJson must not be blank",
            )
        }
        return try {
            json.parseToJsonElement(call.argumentsJson) as? JsonObject
                ?: return ToolResult(
                    callId = call.callId,
                    success = false,
                    errorCode = ErrorCode.ERR_SCHEMA_VIOLATION,
                    errorDetail = "argumentsJson must be a JSON object",
                )
            null // valid
        } catch (e: Exception) {
            ToolResult(
                callId = call.callId,
                success = false,
                errorCode = ErrorCode.ERR_SCHEMA_VIOLATION,
                errorDetail = "argumentsJson parse error: ${e.message}",
            )
        }
    }
}
