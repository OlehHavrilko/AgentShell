package dev.agentshell.rules

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuleEngineTest {

    private fun engine(vararg rules: RiskRule, defaultScore: Int = 30) =
        RuleEngine(RiskRuleSet(defaultScore = defaultScore, rules = rules.toList()))

    @Test
    fun `no rules — returns defaultScore`() {
        val engine = RuleEngine(RiskRuleSet(defaultScore = 25))
        val eval = engine.evaluate("c1", "shell_exec", """{"command":"echo hi"}""")
        assertEquals(25, eval.score)
        assertFalse(eval.blocked)
        assertFalse(eval.requiresApproval)
        assertNull(eval.matchedRule)
    }

    @Test
    fun `tool exact match sets score`() {
        val engine = engine(RiskRule(name = "shell-rule", tool = "shell_exec", score = 45))
        val eval = engine.evaluate("c1", "shell_exec", """{"command":"ls"}""")
        assertEquals(45, eval.score)
        assertEquals("shell-rule", eval.matchedRule)
    }

    @Test
    fun `tool mismatch does not match`() {
        val engine = engine(RiskRule(name = "git-rule", tool = "git_exec", score = 80))
        val eval = engine.evaluate("c1", "shell_exec", """{"command":"ls"}""")
        assertEquals(30, eval.score) // default
        assertNull(eval.matchedRule)
    }

    @Test
    fun `pattern regex match`() {
        val engine = engine(RiskRule(name = "sudo-rule", pattern = "\\bsudo\\b", score = 80))
        val eval = engine.evaluate("c1", "shell_exec", """{"command":"sudo apt-get update"}""")
        assertEquals(80, eval.score)
        assertEquals("sudo-rule", eval.matchedRule)
    }

    @Test
    fun `pattern no match falls through to default`() {
        val engine = engine(RiskRule(name = "rm-rf", pattern = "rm -rf", score = 100))
        val eval = engine.evaluate("c1", "shell_exec", """{"command":"ls -la"}""")
        assertEquals(30, eval.score)
    }

    @Test
    fun `block rule returns blocked=true with ToolResult`() {
        val engine = engine(RiskRule(name = "block-rm-rf", pattern = "rm -rf", block = true))
        val eval = engine.evaluate("c1", "shell_exec", """{"command":"rm -rf /"}""")
        assertTrue(eval.blocked)
        assertNotNull(eval.blockResult)
        assertFalse(eval.blockResult!!.success)
        assertEquals("block-rm-rf", eval.matchedRule)
    }

    @Test
    fun `requireApproval rule sets flag`() {
        val engine = engine(RiskRule(name = "push-main", tool = "git_exec", pattern = "push.*main", score = 90, requireApproval = true))
        val eval = engine.evaluate("c1", "git_exec", """{"subcommand":"push","args":"origin main"}""")
        assertTrue(eval.requiresApproval)
        assertEquals(90, eval.score)
    }

    @Test
    fun `addScore adds to default`() {
        val engine = engine(RiskRule(name = "add10", tool = "shell_exec", addScore = 20), defaultScore = 30)
        val eval = engine.evaluate("c1", "shell_exec", """{}""")
        assertEquals(50, eval.score)
    }

    @Test
    fun `score is capped at 100`() {
        val engine = engine(RiskRule(name = "high", addScore = 200), defaultScore = 30)
        val eval = engine.evaluate("c1", "shell_exec", """{}""")
        assertEquals(100, eval.score)
    }

    @Test
    fun `first matching rule wins`() {
        val engine = engine(
            RiskRule(name = "first", pattern = "echo", score = 10),
            RiskRule(name = "second", pattern = "echo", score = 99),
        )
        val eval = engine.evaluate("c1", "shell_exec", """{"command":"echo hi"}""")
        assertEquals(10, eval.score)
        assertEquals("first", eval.matchedRule)
    }

    @Test
    fun `load default risk-rules yaml from classpath`() {
        val engine = RuleEngine.loadFromClasspath()
        // rm -rf should be blocked
        val blocked = engine.evaluate("c1", "shell_exec", """{"command":"rm -rf /tmp"}""")
        assertTrue(blocked.blocked, "Expected rm -rf to be blocked")
        // git status should be low risk
        val status = engine.evaluate("c1", "git_exec", """{"subcommand":"status"}""")
        assertTrue(status.score <= 20, "Expected git status to be low risk, got ${status.score}")
    }
}
