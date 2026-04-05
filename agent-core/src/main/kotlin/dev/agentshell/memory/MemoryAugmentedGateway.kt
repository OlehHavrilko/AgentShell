package dev.agentshell.memory

import dev.agentshell.llm.LlmGateway
import dev.agentshell.llm.LlmMessage
import dev.agentshell.llm.LlmResponse
import dev.agentshell.llm.LlmTool

/**
 * Wraps any LlmGateway and prepends relevant past-run memories into
 * the system prompt before each completion request.
 *
 * Retrieves top-K memories by TF-IDF cosine similarity to the user's last message.
 */
class MemoryAugmentedGateway(
    private val delegate: LlmGateway,
    private val memoryStore: MemoryStore,
    private val topK: Int = 3
) : LlmGateway {

    override fun complete(
        messages: List<LlmMessage>,
        tools: List<LlmTool>,
        systemPrompt: String?
    ): LlmResponse {
        val query = messages.lastOrNull { it.role == LlmMessage.Role.user }?.content ?: ""
        val memories = memoryStore.search(query, topK)

        val augmentedSystem = if (memories.isEmpty()) systemPrompt
        else buildAugmentedPrompt(systemPrompt, memories)

        return delegate.complete(messages, tools, augmentedSystem)
    }

    private fun buildAugmentedPrompt(base: String?, memories: List<MemoryEntry>): String {
        val memoryBlock = buildString {
            appendLine("=== Relevant past experiences ===")
            memories.forEachIndexed { i, m ->
                appendLine("[Memory ${i + 1}] run=${m.runId.take(8)} agent=${m.agentId} score=${"%.2f".format(m.score)}")
                appendLine(m.text)
                if (i < memories.lastIndex) appendLine("---")
            }
            append("=== End of memories ===")
        }
        return if (base.isNullOrBlank()) memoryBlock else "$base\n\n$memoryBlock"
    }
}

