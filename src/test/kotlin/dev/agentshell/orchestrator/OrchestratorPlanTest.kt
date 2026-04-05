package dev.agentshell.orchestrator

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrchestratorPlanTest {

    @Test
    fun `topological sort — no deps runs in single wave`() {
        val plan = OrchestratorPlan(
            name = "test",
            agents = listOf(
                AgentSpec("a", goal = "goal a"),
                AgentSpec("b", goal = "goal b"),
                AgentSpec("c", goal = "goal c")
            )
        )
        val waves = OrchestratorPlanLoader.topologicalSort(plan)
        assertEquals(1, waves.size)
        assertEquals(3, waves[0].size)
    }

    @Test
    fun `topological sort — linear dependency chain`() {
        val plan = OrchestratorPlan(
            name = "chain",
            agents = listOf(
                AgentSpec("step1", goal = "first"),
                AgentSpec("step2", goal = "second", dependsOn = listOf("step1")),
                AgentSpec("step3", goal = "third", dependsOn = listOf("step2"))
            )
        )
        val waves = OrchestratorPlanLoader.topologicalSort(plan)
        assertEquals(3, waves.size)
        assertEquals("step1", waves[0][0].id)
        assertEquals("step2", waves[1][0].id)
        assertEquals("step3", waves[2][0].id)
    }

    @Test
    fun `topological sort — parallel then merge`() {
        val plan = OrchestratorPlan(
            name = "parallel-merge",
            agents = listOf(
                AgentSpec("scanner", goal = "scan"),
                AgentSpec("reviewer", goal = "review"),
                AgentSpec("reporter", goal = "report", dependsOn = listOf("scanner", "reviewer"))
            )
        )
        val waves = OrchestratorPlanLoader.topologicalSort(plan)
        assertEquals(2, waves.size)
        assertEquals(2, waves[0].size)  // scanner + reviewer in parallel
        assertEquals("reporter", waves[1][0].id)
    }

    @Test
    fun `topological sort — detects circular dependency`() {
        val plan = OrchestratorPlan(
            name = "circular",
            agents = listOf(
                AgentSpec("a", goal = "a", dependsOn = listOf("b")),
                AgentSpec("b", goal = "b", dependsOn = listOf("a"))
            )
        )
        assertFailsWith<IllegalArgumentException> {
            OrchestratorPlanLoader.topologicalSort(plan)
        }
    }

    @Test
    fun `load built-in plan from classpath`() {
        val plan = OrchestratorPlanLoader.loadFromClasspath("code-review-then-commit")
        assertEquals("code-review-then-commit", plan.name)
        assertEquals(2, plan.agents.size)
        assertTrue(plan.agents.any { it.id == "reviewer" })
        assertTrue(plan.agents.any { it.id == "committer" })
    }
}
