package com.agentshell.app.utils

import android.content.Context
import android.util.Log
import dalvik.system.PathClassLoader
import dev.agentshell.plugin.AgentPlugin
import dev.agentshell.llm.provider.LlmProviderRegistry
import java.io.File
import java.util.jar.JarFile

/**
 * Android-specific plugin loader using [PathClassLoader] / [dalvik.system.DexClassLoader].
 *
 * Scans `filesDir/plugins/` for `.jar` / `.apk` / `.dex` archives that contain
 * [AgentPlugin] implementations registered via `Plugin-Class` in MANIFEST.MF.
 *
 * Usage:
 * ```kotlin
 * DexPluginLoader(context).loadAll(LlmProviderRegistry)
 * ```
 */
class DexPluginLoader(private val context: Context) {

    private val pluginDir = File(context.filesDir, "plugins")
    private val optimizedDir = File(context.cacheDir, "dex-oat").also { it.mkdirs() }
    private val loaded = mutableListOf<AgentPlugin>()

    fun loadAll(
        registry: LlmProviderRegistry = LlmProviderRegistry,
        extraContext: Map<String, String> = emptyMap(),
    ): List<AgentPlugin> {
        if (!pluginDir.exists()) {
            Log.d(TAG, "Plugin directory not found: ${pluginDir.absolutePath}")
            return emptyList()
        }

        val archives = pluginDir.listFiles { f ->
            f.isFile && f.extension in listOf("jar", "apk", "dex")
        } ?: emptyArray()

        Log.i(TAG, "Found ${archives.size} plugin archive(s)")

        for (archive in archives) {
            runCatching { loadArchive(archive, registry, extraContext) }
                .onFailure { e -> Log.e(TAG, "Failed to load plugin '${archive.name}': ${e.message}", e) }
        }
        return loaded.toList()
    }

    fun unloadAll() {
        loaded.forEach { plugin ->
            runCatching { plugin.onUnload() }
                .onFailure { e -> Log.w(TAG, "Plugin '${plugin.id}' onUnload error: ${e.message}") }
        }
        loaded.clear()
    }

    private fun loadArchive(
        archive: File,
        registry: LlmProviderRegistry,
        extraContext: Map<String, String>,
    ) {
        val pluginClassName = JarFile(archive).use { jf ->
            jf.manifest?.mainAttributes?.getValue("Plugin-Class")
                ?: error("No 'Plugin-Class' in MANIFEST.MF of '${archive.name}'")
        }

        val classLoader = dalvik.system.DexClassLoader(
            archive.absolutePath,
            optimizedDir.absolutePath,
            null,
            this::class.java.classLoader
        )

        val pluginClass = classLoader.loadClass(pluginClassName)
        require(AgentPlugin::class.java.isAssignableFrom(pluginClass)) {
            "'$pluginClassName' does not implement AgentPlugin"
        }

        val envContext = System.getenv().entries.associate { it.key to it.value } + extraContext
        val plugin = pluginClass.getDeclaredConstructor().newInstance() as AgentPlugin
        plugin.onLoad(registry, envContext)
        loaded.add(plugin)
        Log.i(TAG, "Plugin '${plugin.id}' v${plugin.version} loaded from '${archive.name}'")
    }

    companion object {
        private const val TAG = "DexPluginLoader"
    }
}
