package dev.agentshell.rules

import kotlinx.serialization.Serializable

/**
 * A single risk rule loaded from YAML configuration.
 *
 * Matching logic (all specified fields must match):
 *   - [tool]    — exact tool name match (null = any tool)
 *   - [pattern] — regex match against argumentsJson (null = any args)
 *
 * Effect fields (first matching rule wins):
 *   - [score]           — override the risk score with this value (0-100)
 *   - [addScore]        — add this value to the base score
 *   - [requireApproval] — force approval regardless of threshold
 *   - [block]           — immediately reject (ERR_SANDBOX_KILLED)
 */
@Serializable
data class RiskRule(
    val name: String = "",
    val tool: String? = null,
    val pattern: String? = null,
    val score: Int? = null,
    val addScore: Int = 0,
    val requireApproval: Boolean = false,
    val block: Boolean = false,
)

@Serializable
data class RiskRuleSet(
    val defaultScore: Int = 30,
    val rules: List<RiskRule> = emptyList(),
)
