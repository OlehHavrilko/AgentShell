package dev.agentshell.llm.provider.impl

import dev.agentshell.llm.LlmMessage
import dev.agentshell.llm.provider.CompletionRequest
import dev.agentshell.llm.provider.CompletionResponse
import dev.agentshell.llm.provider.LlmException
import dev.agentshell.llm.provider.LlmProvider
import dev.agentshell.llm.provider.ProviderYaml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File

class LlamaCppProvider(private val cfg: ProviderYaml) : LlmProvider {
    override val id = "llama_cpp"
    override val displayName = "Llama.cpp (Local)"
    override val isLocal = true
    private val log = LoggerFactory.getLogger(LlamaCppProvider::class.java)
    private val binaryPath get() = cfg.binaryPath ?: "llama-cli"
    private val modelPath get() = cfg.modelPath ?: ""

    override suspend fun complete(request: CompletionRequest): CompletionResponse = withContext(Dispatchers.IO) {
        if (modelPath.isBlank()) throw LlmException("llama_cpp: modelPath not configured")
        val prompt = request.messages.joinToString("\n") { msg ->
            val prefix = when (msg.role) {
                LlmMessage.Role.system -> "[SYSTEM]"
                LlmMessage.Role.user -> "[USER]"
                LlmMessage.Role.assistant -> "[ASSISTANT]"
                LlmMessage.Role.tool -> "[TOOL]"
            }
            "$prefix ${msg.content ?: ""}"
        } + "\n[ASSISTANT]"

        val cmd = mutableListOf(
            binaryPath,
            "-m", modelPath,
            "--prompt", prompt,
            "--temp", request.temperature.toString(),
            "-n", request.maxTokens.toString(),
            "-t", cfg.threads.toString(),
            "--no-display-prompt",
        )
        request.systemPrompt?.let { cmd.addAll(listOf("--system-prompt", it)) }

        log.debug("llama.cpp cmd: {} -m {} ...", binaryPath, File(modelPath).name)
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        val exit = proc.waitFor()
        if (exit != 0) throw LlmException("llama-cpp exited $exit: ${output.take(200)}")

        CompletionResponse(
            content = output.trim(),
            providerId = id,
            modelUsed = File(modelPath).name,
        )
    }
}
