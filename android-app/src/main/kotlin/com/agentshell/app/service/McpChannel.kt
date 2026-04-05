package com.agentshell.app.service

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream

@Serializable
data class AgentMessage(
    val type: String,
    val runId: String? = null,
    val payload: String? = null
)

class McpChannel(
    private val input: InputStream,
    private val output: OutputStream
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _incoming = MutableSharedFlow<AgentMessage>(extraBufferCapacity = 100)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init { startReading() }

    private fun startReading() = scope.launch {
        val reader = BufferedReader(InputStreamReader(input))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            line?.takeIf { it.isNotBlank() }?.let {
                runCatching { json.decodeFromString<AgentMessage>(it) }
                    .onSuccess { msg -> _incoming.tryEmit(msg) }
            }
        }
    }

    suspend fun send(msg: AgentMessage) = withContext(Dispatchers.IO) {
        val text = json.encodeToString(msg) + "\n"
        output.write(text.toByteArray())
        output.flush()
    }

    fun receive(): Flow<AgentMessage> = _incoming.asSharedFlow()

    fun close() {
        scope.cancel()
        runCatching { input.close() }
        runCatching { output.close() }
    }
}
