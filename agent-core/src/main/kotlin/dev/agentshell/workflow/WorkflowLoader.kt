package dev.agentshell.workflow

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import dev.agentshell.domain.RiskLevel
import dev.agentshell.domain.ToolCall
import dev.agentshell.domain.ToolContract
import dev.agentshell.runtime.AgentRunner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

object WorkflowLoader {

    private val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))
    private val json = Json { ignoreUnknownKeys = true }

    fun load(path: String): WorkflowDefinition {
        val file = File(path)
        require(file.exists()) { "Workflow file not found: $path" }
        return when {
            path.endsWith(".yaml") || path.endsWith(".yml") ->
                yaml.decodeFromString(WorkflowDefinition.serializer(), file.readText())
            path.endsWith(".json") ->
                json.decodeFromString(WorkflowDefinition.serializer(), file.readText())
            else -> error("Unsupported workflow format (use .yaml, .yml, or .json): $path")
        }
    }

    fun toRunConfig(agentId: String, def: WorkflowDefinition): AgentRunner.RunConfig {
        val steps = def.steps.map { step ->
            AgentRunner.StepDef(
                call = ToolCall(
                    callId = step.id,
                    toolName = step.tool,
                    argumentsJson = argsToJson(step.args),
                    idempotencyKey = step.idempotencyKey,
                ),
                contract = contractFor(step.tool),
            )
        }
        return AgentRunner.RunConfig(
            agentId = agentId,
            steps = steps,
            riskThreshold = def.riskThreshold,
            isNewRepo = def.isNewRepo,
        )
    }

    /** Converts a flat string map to a JSON object string. */
    private fun argsToJson(args: Map<String, String>): String {
        val jsonMap = args.mapValues { JsonPrimitive(it.value) }
        return JsonObject(jsonMap).toString()
    }

    /** Infers a ToolContract from the tool name. */
    private fun contractFor(toolName: String): ToolContract {
        val (risk, policy) = when (toolName) {
            "git_push"  -> RiskLevel.HIGH to "git_default"
            "shell_exec" -> RiskLevel.MEDIUM to "shell_default"
            "file_write" -> RiskLevel.MEDIUM to "shell_default"
            else         -> RiskLevel.LOW to "shell_default"
        }
        return ToolContract(
            name = toolName,
            version = "1.0",
            description = toolName,
            riskLevel = risk,
            sandboxPolicy = policy,
        )
    }
}
