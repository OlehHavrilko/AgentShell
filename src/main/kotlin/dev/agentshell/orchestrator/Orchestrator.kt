package dev.agentshell.orchestrator

import dev.agentshell.audit.AuditTrail
import dev.agentshell.llm.LlmGateway
import dev.agentshell.memory.MemoryAugmentedGateway
import dev.agentshell.memory.MemoryIndexer
import dev.agentshell.memory.MemoryStore
import dev.agentshell.runtime.AgentRunner
import dev.agentshell.state.StateStore
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Orchestrates multiple agents defined in an OrchestratorPlan.
 *
 * - Resolves execution order via topological sort (respects dependsOn)
 * - Runs agents within a wave in parallel on a thread pool
 * - Injects upstream agent results into downstream agent goals as [UPSTREAM_RESULT]
 * - Optionally integrates with MemoryStore for cross-agent memory
 */
class Orchestrator(
    private val stateStore: StateStore,
    private val gatewayFactory: (AgentSpec) -> LlmGateway?,
    private val auditTrailFactory: ((String) -> AuditTrail)? = null,
    private val memoryStore: MemoryStore? = null,
    private val parallelism: Int = 4
) {

    private val log = LoggerFactory.getLogger(Orchestrator::class.java)
    private val pool = Executors.newFixedThreadPool(parallelism)

    data class AgentResult(
        val agentId: String,
        val runId: String,
        val status: String,
        val summary: String?
    )

    data class OrchestrationResult(
        val planName: String,
        val agentResults: List<AgentResult>,
        val success: Boolean
    )

    fun run(plan: OrchestratorPlan): OrchestrationResult {
        log.info("Starting orchestration plan '${plan.name}' with ${plan.agents.size} agents")
        val waves = OrchestratorPlanLoader.topologicalSort(plan)
        val allResults = mutableListOf<AgentResult>()
        val resultsByAgent = mutableMapOf<String, AgentResult>()

        for ((waveIdx, wave) in waves.withIndex()) {
            log.info("Wave ${waveIdx + 1}/${waves.size}: running ${wave.map { it.id }}")
            val futures: List<Future<AgentResult>> = wave.map { spec ->
                pool.submit<AgentResult> {
                    runAgent(spec, resultsByAgent)
                }
            }
            val waveResults = futures.map { it.get() }
            allResults.addAll(waveResults)
            waveResults.forEach { resultsByAgent[it.agentId] = it }

            val failed = waveResults.filter { it.status != "COMPLETED" }
            if (failed.isNotEmpty()) {
                log.warn("Wave ${waveIdx + 1} had failures: ${failed.map { it.agentId }}. Stopping.")
                return OrchestrationResult(plan.name, allResults, success = false)
            }
        }

        log.info("Orchestration '${plan.name}' completed. ${allResults.size} agents ran successfully.")
        return OrchestrationResult(plan.name, allResults, success = true)
    }

    private fun runAgent(spec: AgentSpec, upstreamResults: Map<String, AgentResult>): AgentResult {
        log.info("Running agent '${spec.id}' (preset=${spec.preset}, provider=${spec.provider})")

        val goal = buildGoal(spec, upstreamResults)
        var gateway = gatewayFactory(spec)
        if (gateway == null) {
            log.warn("No gateway for agent '${spec.id}', skipping")
            return AgentResult(spec.id, "", "SKIPPED", null)
        }

        if (memoryStore != null) {
            gateway = MemoryAugmentedGateway(gateway, memoryStore)
        }

        val auditTrail = auditTrailFactory?.invoke(spec.id)
        val runner = AgentRunner(
            stateStore = stateStore,
            auditTrail = auditTrail,
            gateway = gateway
        )

        val config = AgentRunner.AgenticConfig(
            agentId = spec.id,
            goal = goal,
            riskThreshold = spec.riskThreshold,
            maxIterations = spec.maxIterations
        )

        return try {
            val outcome = runner.executeAgentic(config)
            val summary = outcome.finalMessage?.take(200)

            // Index into memory if available
            if (memoryStore != null && auditTrail != null) {
                MemoryIndexer(memoryStore, auditTrail, stateStore).indexRun(outcome.runId)
            }

            AgentResult(spec.id, outcome.runId, outcome.status.name, summary)
        } catch (e: Exception) {
            log.error("Agent '${spec.id}' failed: ${e.message}", e)
            AgentResult(spec.id, "", "FAILED", e.message)
        }
    }

    private fun buildGoal(spec: AgentSpec, upstreamResults: Map<String, AgentResult>): String {
        val base = spec.goal ?: "Complete the task as defined in preset '${spec.preset}'"

        if (spec.dependsOn.isEmpty()) return base

        val upstreamContext = spec.dependsOn.mapNotNull { depId ->
            upstreamResults[depId]?.let { r ->
                "[UPSTREAM_RESULT from ${r.agentId}]\n${r.summary ?: "(no summary)"}"
            }
        }.joinToString("\n\n")

        return if (upstreamContext.isBlank()) base
        else "$base\n\nContext from upstream agents:\n$upstreamContext"
    }

    fun shutdown() {
        pool.shutdown()
    }
}
