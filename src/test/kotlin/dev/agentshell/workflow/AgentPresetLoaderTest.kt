package dev.agentshell.workflow

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentPresetLoaderTest {

    @Test
    fun `load built-in code-review preset`() {
        val preset = AgentPresetLoader.load("code-review")
        assertEquals("code-review", preset.name)
        assertTrue(preset.goal.contains("git diff"), "Goal should mention git diff")
        assertTrue(preset.riskThreshold > 0)
    }

    @Test
    fun `load built-in git-workflow preset`() {
        val preset = AgentPresetLoader.load("git-workflow")
        assertEquals("git-workflow", preset.name)
        assertTrue(preset.goal.isNotBlank())
    }

    @Test
    fun `load built-in project-scan preset`() {
        val preset = AgentPresetLoader.load("project-scan")
        assertEquals("project-scan", preset.name)
        assertTrue(preset.goal.contains("REPORT.md"), "Goal should mention REPORT.md")
    }

    @Test
    fun `toAgenticConfig maps preset fields`() {
        val preset = AgentPresetLoader.load("code-review")
        val config = AgentPresetLoader.toAgenticConfig(preset, "test-agent")
        assertEquals("test-agent", config.agentId)
        assertEquals(preset.goal.trim(), config.goal)
        assertEquals(preset.riskThreshold, config.riskThreshold)
        assertEquals(preset.maxIterations, config.maxIterations)
    }

    @Test
    fun `listBuiltIn returns expected names`() {
        val list = AgentPresetLoader.listBuiltIn()
        assertTrue(list.contains("code-review"))
        assertTrue(list.contains("git-workflow"))
        assertTrue(list.contains("project-scan"))
    }

    @Test
    fun `unknown preset throws with helpful message`() {
        val ex = runCatching { AgentPresetLoader.load("nonexistent-preset") }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex!!.message!!.contains("nonexistent-preset"))
    }
}
