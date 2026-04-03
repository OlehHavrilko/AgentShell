package dev.agentshell.domain

import kotlinx.serialization.Serializable

@Serializable
data class SandboxPolicy(
    val name: String,
    val cpuTimeMs: Long,
    val wallTimeMs: Long,
    val memoryBytes: Long,
    val outputBytes: Long,
    val allowedPaths: List<String>,
    val network: Boolean,
    val allowedHosts: List<String> = emptyList(),
)

object SandboxDefaults {
    val shellDefault = SandboxPolicy(
        name = "shell_default",
        cpuTimeMs = 30_000,
        wallTimeMs = 60_000,
        memoryBytes = 256_000_000,
        outputBytes = 1_048_576,
        allowedPaths = listOf("\$REPO_ROOT/**", "\$HOME/.config/agentshell/**"),
        network = false,
    )

    val gitDefault = shellDefault.copy(
        name = "git_default",
        network = true,
        allowedHosts = listOf("github.com", "gitlab.com", "bitbucket.org"),
    )
}
