package dev.agentshell.workflow

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import dev.agentshell.runtime.AgentRunner
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class AgentPreset(
    val name: String,
    val description: String = "",
    val goal: String,
    val systemPrompt: String = "",
    val riskThreshold: Int = 60,
    val maxIterations: Int = 20,
)

/**
 * Loads agent presets from YAML files.
 *
 * Built-in presets are in `agents/` on the classpath.
 * External presets can be loaded from a file path.
 *
 * Usage:
 *   --preset code-review          loads classpath:agents/code-review.yaml
 *   --preset /path/my-agent.yaml  loads from file system
 */
object AgentPresetLoader {

    private val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))

    fun load(nameOrPath: String): AgentPreset {
        // Try file system first
        val file = File(nameOrPath)
        if (file.exists()) return parse(file.readText())

        // Try classpath with .yaml suffix
        val resource = "agents/${nameOrPath.removeSuffix(".yaml")}.yaml"
        val url = AgentPresetLoader::class.java.classLoader.getResource(resource)
            ?: error("Agent preset not found: '$nameOrPath'. Built-in: code-review, git-workflow, project-scan")
        return parse(url.readText())
    }

    fun toAgenticConfig(preset: AgentPreset, agentId: String): AgentRunner.AgenticConfig =
        AgentRunner.AgenticConfig(
            agentId = agentId,
            goal = preset.goal.trim(),
            systemPrompt = preset.systemPrompt.trim().ifBlank { AgentRunner.DEFAULT_SYSTEM_PROMPT },
            riskThreshold = preset.riskThreshold,
            maxIterations = preset.maxIterations,
        )

    fun listBuiltIn(): List<String> = listOf("code-review", "git-workflow", "project-scan")

    private fun parse(text: String): AgentPreset =
        yaml.decodeFromString(AgentPreset.serializer(), text)
}
