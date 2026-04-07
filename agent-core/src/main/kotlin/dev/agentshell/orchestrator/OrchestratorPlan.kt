package dev.agentshell.orchestrator

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Retry configuration for an [AgentSpec].
 *
 * @param maxAttempts Total number of attempts including the first. Defaults to 1 (no retry).
 * @param delayMs     Delay in ms between attempts.
 * @param retryOn     Status values that trigger a retry (e.g. ["FAILED","CRASHED"]).
 */
@Serializable
data class RetryConfig(
    val maxAttempts: Int = 1,
    val delayMs: Long = 0L,
    val retryOn: List<String> = listOf("FAILED", "CRASHED"),
)

@Serializable
data class AgentSpec(
    val id: String,
    val preset: String? = null,
    val goal: String? = null,
    val provider: String = "ollama",
    val model: String? = null,
    val riskThreshold: Int = 60,
    val maxIterations: Int = 20,
    @SerialName("dependsOn") val dependsOn: List<String> = emptyList(),
    /**
     * Optional condition expression evaluated against upstream results.
     *
     * Syntax: `<agentId>.<field> <op> <value>`
     * Examples:
     *   - `code_review.status == COMPLETED`  — run only if code_review succeeded
     *   - `linter.status != FAILED`          — skip if linter failed
     *   - `scanner.summary contains critical`
     *
     * If omitted, the agent always runs (subject to dependency resolution).
     */
    val condition: String? = null,
    /**
     * Retry policy. Defaults to single attempt (no retries).
     */
    val retry: RetryConfig = RetryConfig(),
)

@Serializable
data class OrchestratorPlan(
    val name: String = "unnamed-plan",
    val agents: List<AgentSpec>
)

object OrchestratorPlanLoader {

    fun load(path: String): OrchestratorPlan {
        val file = File(path)
        require(file.exists()) { "Orchestrator plan not found: $path" }
        return Yaml.default.decodeFromString(OrchestratorPlan.serializer(), file.readText())
    }

    fun loadFromClasspath(name: String): OrchestratorPlan {
        val stream = OrchestratorPlanLoader::class.java.classLoader
            .getResourceAsStream("orchestrator/$name.yaml")
            ?: throw IllegalArgumentException("Built-in plan not found: orchestrator/$name.yaml")
        return Yaml.default.decodeFromString(OrchestratorPlan.serializer(), stream.bufferedReader().readText())
    }

    /** Topological sort: agents with no deps first, then those depending on them. */
    fun topologicalSort(plan: OrchestratorPlan): List<List<AgentSpec>> {
        val remaining = plan.agents.toMutableList()
        val done = mutableSetOf<String>()
        val waves = mutableListOf<List<AgentSpec>>()

        while (remaining.isNotEmpty()) {
            val wave = remaining.filter { spec -> spec.dependsOn.all { it in done } }
            require(wave.isNotEmpty()) {
                "Circular dependency or missing agent in plan '${plan.name}'. Remaining: ${remaining.map { it.id }}"
            }
            waves.add(wave)
            done.addAll(wave.map { it.id })
            remaining.removeAll(wave)
        }
        return waves
    }
}
