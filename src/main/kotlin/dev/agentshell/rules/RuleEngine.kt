package dev.agentshell.rules

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import dev.agentshell.domain.ErrorCode
import dev.agentshell.domain.ToolResult
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Evaluates tool calls against a set of [RiskRule]s loaded from YAML.
 *
 * Replaces the hardcoded pattern-matching in [dev.agentshell.runtime.RiskScorer]
 * with a fully declarative, user-configurable rule set.
 *
 * Default rules file: `src/main/resources/rules/risk-rules.yaml` (loaded from classpath).
 * Override at runtime via the constructor or [load].
 */
class RuleEngine(private val ruleSet: RiskRuleSet = RiskRuleSet()) {

    private val log = LoggerFactory.getLogger(RuleEngine::class.java)

    data class Evaluation(
        val score: Int,
        val requiresApproval: Boolean,
        val blocked: Boolean,
        val matchedRule: String?,
        val blockResult: ToolResult? = null,
    )

    fun evaluate(callId: String, toolName: String, argumentsJson: String): Evaluation {
        var score = ruleSet.defaultScore
        var requireApproval = false

        for (rule in ruleSet.rules) {
            if (!matches(rule, toolName, argumentsJson)) continue

            log.debug("Rule '{}' matched tool={}", rule.name, toolName)

            if (rule.block) {
                log.warn("Rule '{}' BLOCKS tool={} args={}", rule.name, toolName, argumentsJson.take(100))
                return Evaluation(
                    score = 100,
                    requiresApproval = false,
                    blocked = true,
                    matchedRule = rule.name,
                    blockResult = ToolResult(
                        callId = callId,
                        success = false,
                        errorCode = ErrorCode.ERR_SANDBOX_KILLED,
                        errorDetail = "Blocked by rule '${rule.name}'",
                    ),
                )
            }

            if (rule.score != null) score = rule.score.coerceIn(0, 100)
            score = (score + rule.addScore).coerceIn(0, 100)
            if (rule.requireApproval) requireApproval = true

            return Evaluation(
                score = score,
                requiresApproval = requireApproval,
                blocked = false,
                matchedRule = rule.name,
            )
        }

        return Evaluation(score = score, requiresApproval = false, blocked = false, matchedRule = null)
    }

    private fun matches(rule: RiskRule, toolName: String, argumentsJson: String): Boolean {
        if (rule.tool != null && rule.tool != toolName) return false
        if (rule.pattern != null && !Regex(rule.pattern).containsMatchIn(argumentsJson)) return false
        return true
    }

    companion object {
        private val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))

        /** Load from a file path. */
        fun load(path: String): RuleEngine {
            val text = File(path).readText()
            return RuleEngine(yaml.decodeFromString(RiskRuleSet.serializer(), text))
        }

        /** Load from classpath resource (e.g. "rules/risk-rules.yaml"). */
        fun loadFromClasspath(resource: String = "rules/risk-rules.yaml"): RuleEngine {
            val url = RuleEngine::class.java.classLoader.getResource(resource)
                ?: return RuleEngine() // use defaults if not found
            return RuleEngine(yaml.decodeFromString(RiskRuleSet.serializer(), url.readText()))
        }
    }
}
