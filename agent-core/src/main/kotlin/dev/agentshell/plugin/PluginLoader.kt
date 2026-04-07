package dev.agentshell.plugin

import dev.agentshell.llm.provider.LlmProviderRegistry
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile

/**
 * Scans a `plugins/` directory for JAR files and loads any [AgentPlugin] implementations.
 *
 * Each JAR must contain a `MANIFEST.MF` with a `Plugin-Class` attribute pointing to
 * the fully-qualified class name of the [AgentPlugin] implementation.
 *
 * Usage:
 * ```kotlin
 * val loader = PluginLoader(File("plugins"))
 * val plugins = loader.loadAll(LlmProviderRegistry, mapOf("MY_KEY" to "value"))
 * // plugins are registered and ready to use
 * ```
 */
class PluginLoader(private val pluginDir: File) {

    private val log = LoggerFactory.getLogger(PluginLoader::class.java)
    private val loaded = mutableListOf<AgentPlugin>()

    /**
     * Load all JARs in [pluginDir], instantiate each [AgentPlugin], and call [AgentPlugin.onLoad].
     *
     * @param registry Provider registry passed to each plugin.
     * @param context  Config map (environment vars, YAML values) passed to each plugin.
     * @return List of successfully loaded plugins.
     */
    fun loadAll(
        registry: LlmProviderRegistry = LlmProviderRegistry,
        context: Map<String, String> = buildContext(),
    ): List<AgentPlugin> {
        if (!pluginDir.exists()) {
            log.debug("Plugin directory '{}' does not exist — no plugins loaded.", pluginDir.absolutePath)
            return emptyList()
        }

        val jars = pluginDir.listFiles { f -> f.isFile && f.extension == "jar" } ?: emptyArray()
        log.info("Found {} plugin JAR(s) in '{}'", jars.size, pluginDir.absolutePath)

        for (jar in jars) {
            runCatching { loadJar(jar, registry, context) }
                .onFailure { e -> log.error("Failed to load plugin '{}': {}", jar.name, e.message, e) }
        }
        return loaded.toList()
    }

    /** Unload all plugins (calls [AgentPlugin.onUnload] on each). */
    fun unloadAll() {
        loaded.forEach { plugin ->
            runCatching { plugin.onUnload() }
                .onFailure { e -> log.warn("Plugin '{}' onUnload error: {}", plugin.id, e.message) }
        }
        loaded.clear()
    }

    private fun loadJar(jar: File, registry: LlmProviderRegistry, context: Map<String, String>) {
        log.debug("Loading plugin from '{}'", jar.name)

        val pluginClassName = JarFile(jar).use { jf ->
            jf.manifest?.mainAttributes?.getValue("Plugin-Class")
                ?: error("No 'Plugin-Class' entry in MANIFEST.MF of '${jar.name}'")
        }

        val classLoader = URLClassLoader(
            arrayOf(jar.toURI().toURL()),
            this::class.java.classLoader
        )

        val pluginClass = classLoader.loadClass(pluginClassName)
        require(AgentPlugin::class.java.isAssignableFrom(pluginClass)) {
            "'$pluginClassName' does not implement AgentPlugin"
        }

        val plugin = pluginClass.getDeclaredConstructor().newInstance() as AgentPlugin
        plugin.onLoad(registry, context)
        loaded.add(plugin)
        log.info("Plugin '{}' v{} loaded from '{}'", plugin.id, plugin.version, jar.name)
    }

    private fun buildContext(): Map<String, String> =
        System.getenv().entries.associate { it.key to it.value }
}
