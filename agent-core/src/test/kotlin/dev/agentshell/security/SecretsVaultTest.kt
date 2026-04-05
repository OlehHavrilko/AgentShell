package dev.agentshell.security

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretsVaultTest {

    @BeforeEach
    @AfterEach
    fun clearVault() = SecretsVault.clear()

    @Test
    fun `register and mask single secret`() {
        SecretsVault.register("sk-abc123")
        val masked = SecretsVault.mask("Bearer sk-abc123 is the key")
        assertEquals("Bearer [REDACTED] is the key", masked)
    }

    @Test
    fun `mask returns original when no secrets registered`() {
        val text = "hello world"
        assertEquals(text, SecretsVault.mask(text))
    }

    @Test
    fun `blank secret is not registered`() {
        SecretsVault.register("")
        SecretsVault.register("   ")
        val masked = SecretsVault.mask("   ")
        assertEquals("   ", masked)
    }

    @Test
    fun `multiple secrets all masked`() {
        SecretsVault.register("secret1")
        SecretsVault.register("secret2")
        val masked = SecretsVault.mask("secret1 and secret2 are here")
        assertFalse(masked.contains("secret1"))
        assertFalse(masked.contains("secret2"))
        assertTrue(masked.contains("[REDACTED]"))
    }

    @Test
    fun `registerLlmKeys does not throw when env vars absent`() {
        // No env vars set — should complete without exception
        SecretsVault.registerLlmKeys()
    }

    @Test
    fun `clearAll removes all secrets`() {
        SecretsVault.register("mysecret")
        SecretsVault.clear()
        val masked = SecretsVault.mask("mysecret")
        assertEquals("mysecret", masked)
    }
}
