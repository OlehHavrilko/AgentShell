package dev.agentshell.security

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe registry of secret values that must be masked in logs and audit events.
 *
 * Usage:
 *   SecretsVault.register("OPENAI_KEY", System.getenv("OPENAI_API_KEY"))
 *   SecretsVault.mask("Bearer sk-abc123...") // → "Bearer [REDACTED]"
 *
 * Automatically called by [registerLlmKeys] to mask all known LLM API keys.
 */
object SecretsVault {

    private val secrets = CopyOnWriteArrayList<String>()
    private const val REDACTED = "[REDACTED]"

    /** Register a secret value to be masked everywhere it appears. */
    fun register(value: String) {
        if (value.isNotBlank() && value.length > 4) {
            secrets.addIfAbsent(value)
        }
    }

    /** Register a named secret from an environment variable (no-op if env var is unset). */
    fun registerEnv(envVar: String) {
        System.getenv(envVar)?.let { register(it) }
    }

    /** Register all known LLM API key environment variables. */
    fun registerLlmKeys() {
        registerEnv("OPENAI_API_KEY")
        registerEnv("OPENROUTER_API_KEY")
        registerEnv("GEMINI_API_KEY")
        registerEnv("GOOGLE_API_KEY")
        registerEnv("ANTHROPIC_API_KEY")
    }

    /** Replace all registered secrets in the given string with [REDACTED]. */
    fun mask(input: String): String {
        var result = input
        for (secret in secrets) {
            result = result.replace(secret, REDACTED)
        }
        return result
    }

    /** Mask secrets in an audit event's string fields. */
    fun maskAuditFields(vararg fields: String?): List<String?> =
        fields.map { it?.let { mask(it) } }

    /** Clear all registered secrets (used in tests). */
    fun clear() = secrets.clear()

    /** Returns count of registered secrets (for testing). */
    val size: Int get() = secrets.size
}
