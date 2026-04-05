package dev.agentshell.workflow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertEquals

class WorkflowLoaderTest {

    @Test
    fun `load valid yaml workflow`() {
        val yaml = """
            name: test-pipeline
            riskThreshold: 50
            steps:
              - id: step-1
                tool: shell_exec
                args:
                  command: echo hello
        """.trimIndent()
        val file = File.createTempFile("workflow-", ".yaml").also { it.writeText(yaml); it.deleteOnExit() }

        val def = WorkflowLoader.load(file.absolutePath)

        assertEquals("test-pipeline", def.name)
        assertEquals(50, def.riskThreshold)
        assertEquals(1, def.steps.size)
        assertEquals("step-1", def.steps[0].id)
        assertEquals("shell_exec", def.steps[0].tool)
        assertEquals(mapOf("command" to "echo hello"), def.steps[0].args)
    }

    @Test
    fun `load yaml with multiple steps`() {
        val yaml = """
            name: multi-step
            steps:
              - id: s1
                tool: shell_exec
                args:
                  command: echo one
              - id: s2
                tool: file_write
                args:
                  operation: write
                  path: /tmp/test.txt
                  content: hello
        """.trimIndent()
        val file = File.createTempFile("workflow-", ".yml").also { it.writeText(yaml); it.deleteOnExit() }

        val def = WorkflowLoader.load(file.absolutePath)

        assertEquals(2, def.steps.size)
        assertEquals("s2", def.steps[1].id)
        assertEquals("file_write", def.steps[1].tool)
    }

    @Test
    fun `toRunConfig converts workflow to RunConfig`() {
        val def = WorkflowDefinition(
            name = "pipe",
            riskThreshold = 70,
            steps = listOf(
                StepDefinition(id = "s1", tool = "shell_exec", args = mapOf("command" to "ls"))
            )
        )

        val config = WorkflowLoader.toRunConfig("my-agent", def)

        assertEquals("my-agent", config.agentId)
        assertEquals(70, config.riskThreshold)
        assertEquals(1, config.steps.size)
        assertEquals("s1", config.steps[0].call.callId)
        assertEquals("shell_exec", config.steps[0].call.toolName)
        assertEquals("""{"command":"ls"}""", config.steps[0].call.argumentsJson)
    }

    @Test
    fun `throws for missing file`() {
        assertThrows<IllegalArgumentException> {
            WorkflowLoader.load("/nonexistent/path/workflow.yaml")
        }
    }

    @Test
    fun `throws for unsupported extension`() {
        val file = File.createTempFile("workflow-", ".xml").also { it.writeText("<xml/>"); it.deleteOnExit() }

        assertThrows<IllegalStateException> {
            WorkflowLoader.load(file.absolutePath)
        }
    }

    @Test
    fun `default riskThreshold is 60 when not set`() {
        val yaml = """
            name: defaults
            steps: []
        """.trimIndent()
        val file = File.createTempFile("workflow-", ".yaml").also { it.writeText(yaml); it.deleteOnExit() }

        val def = WorkflowLoader.load(file.absolutePath)

        assertEquals(60, def.riskThreshold)
    }
}
