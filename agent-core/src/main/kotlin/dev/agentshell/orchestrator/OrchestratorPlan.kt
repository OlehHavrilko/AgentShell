package dev.agentshell.orchestrator

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class AgentSpec(
    val id: String,
    val preset: String? = null,
    val goal: String? = null,
    val provider: String = "ollama",
    val model: String? = null,
    val riskThreshold: Int = 60,
    val maxIterations: Int = 20,
    @SerialName("dependsOn") val dependsOn: List<String> = emptyList()
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
