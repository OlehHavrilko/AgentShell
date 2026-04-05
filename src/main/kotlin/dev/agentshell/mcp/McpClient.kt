package dev.agentshell.mcp

import dev.agentshell.domain.RiskLevel
import dev.agentshell.domain.ToolContract
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicInteger

/**
 * MCP (Model Context Protocol) client over stdio transport.
 *
 * Launches an MCP server process, performs JSON-RPC handshake,
 * discovers available tools, and exposes them as [ToolContract]s.
 *
 * @param command MCP server command, e.g. ["npx", "-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
 */
class McpClient(private val command: List<String>) : AutoCloseable {

    private val log = LoggerFactory.getLogger(McpClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val idGen = AtomicInteger(1)

    private lateinit var process: Process
    private lateinit var reader: BufferedReader
    private lateinit var writer: BufferedWriter

    /** Connect to the MCP server and perform initialization handshake. */
    fun connect() {
        log.info("Starting MCP server: {}", command.joinToString(" "))
        process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()
        reader = BufferedReader(InputStreamReader(process.inputStream))
        writer = BufferedWriter(OutputStreamWriter(process.outputStream))

        // Initialize
        val initResult = call("initialize", buildJsonObject {
            put("protocolVersion", JsonPrimitive("2024-11-05"))
            put("capabilities", buildJsonObject {
                put("tools", buildJsonObject {})
            })
            put("clientInfo", buildJsonObject {
                put("name", JsonPrimitive("AgentShell"))
                put("version", JsonPrimitive("2.0"))
            })
        })
        log.info("MCP initialized: {}", initResult?.let {
            it.jsonObject["serverInfo"]?.jsonObject?.get("name")?.jsonPrimitive?.content
        } ?: "unknown")

        // Notify initialized
        notify("notifications/initialized")
    }

    /** List all tools exposed by the MCP server. */
    fun listTools(): List<McpTool> {
        val result = call("tools/list", buildJsonObject {}) ?: return emptyList()
        val tools = result.jsonObject["tools"]?.jsonArray ?: return emptyList()
        return tools.mapNotNull { toolEl ->
            try {
                val obj = toolEl.jsonObject
                McpTool(
                    name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    description = obj["description"]?.jsonPrimitive?.content ?: "",
                    inputSchemaJson = obj["inputSchema"]?.let { json.encodeToString(JsonObject.serializer(), it.jsonObject) } ?: "{}",
                )
            } catch (e: Exception) {
                log.warn("Failed to parse MCP tool: {}", e.message)
                null
            }
        }
    }

    /** Call a tool on the MCP server. Returns the result content as a string. */
    fun callTool(toolName: String, argumentsJson: String): McpToolResult {
        val args = try {
            json.parseToJsonElement(argumentsJson).jsonObject
        } catch (e: Exception) {
            return McpToolResult(success = false, content = "Invalid JSON args: ${e.message}")
        }

        val result = call("tools/call", buildJsonObject {
            put("name", JsonPrimitive(toolName))
            put("arguments", args)
        })

        if (result == null) {
            return McpToolResult(success = false, content = "No response from MCP server")
        }

        val contentArr = result.jsonObject["content"]?.jsonArray
        val text = contentArr?.joinToString("\n") { item ->
            item.jsonObject["text"]?.jsonPrimitive?.content ?: item.toString()
        } ?: result.toString()

        val isError = result.jsonObject["isError"]?.jsonPrimitive?.content == "true"
        return McpToolResult(success = !isError, content = text)
    }

    /** Convert MCP tools to AgentShell ToolContracts. */
    fun toToolContracts(tools: List<McpTool>): List<ToolContract> =
        tools.map { t ->
            ToolContract(
                name = "mcp_${t.name}",
                version = "1.0",
                description = t.description,
                riskLevel = RiskLevel.MEDIUM,
                sandboxPolicy = "mcp_default",
            )
        }

    private fun call(method: String, params: JsonObject): kotlinx.serialization.json.JsonElement? {
        val id = idGen.getAndIncrement()
        val request = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(id))
            put("method", JsonPrimitive(method))
            put("params", params)
        }
        writer.write(json.encodeToString(JsonObject.serializer(), request))
        writer.newLine()
        writer.flush()

        // Read until we get a response matching our id
        var line: String?
        repeat(100) {
            line = reader.readLine() ?: return null
            if (line!!.isBlank()) return@repeat
            return try {
                val resp = json.parseToJsonElement(line!!).jsonObject
                val respId = resp["id"]?.jsonPrimitive?.content?.toIntOrNull()
                if (respId == id) {
                    resp["result"] ?: resp["error"]?.also {
                        log.warn("MCP error for method={}: {}", method, it)
                    }
                } else {
                    null // notification or other response — ignore
                }
            } catch (e: Exception) {
                log.debug("MCP parse error: {}", e.message)
                null
            }
        }
        return null
    }

    private fun notify(method: String) {
        val notification = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("method", JsonPrimitive(method))
            put("params", buildJsonObject {})
        }
        writer.write(json.encodeToString(JsonObject.serializer(), notification))
        writer.newLine()
        writer.flush()
    }

    override fun close() {
        runCatching { writer.close() }
        runCatching { reader.close() }
        runCatching { process.destroy() }
        log.info("MCP client closed")
    }
}

data class McpTool(
    val name: String,
    val description: String,
    val inputSchemaJson: String,
)

data class McpToolResult(
    val success: Boolean,
    val content: String,
)
