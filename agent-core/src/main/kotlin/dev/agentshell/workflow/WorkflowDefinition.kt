package dev.agentshell.workflow

import kotlinx.serialization.Serializable

@Serializable
data class WorkflowDefinition(
    val name: String,
    val description: String = "",
    val riskThreshold: Int = 60,
    val isNewRepo: Boolean = false,
    val steps: List<StepDefinition>,
)

@Serializable
data class StepDefinition(
    val id: String,
    val tool: String,
    val args: Map<String, String> = emptyMap(),
    val idempotencyKey: String? = null,
    val description: String = "",
)
