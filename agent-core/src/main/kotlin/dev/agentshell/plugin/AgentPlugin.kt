package dev.agentshell.plugin

import dev.agentshell.llm.provider.LlmProviderRegistry

/**
 * Contract for AgentShell plugins loaded from external JAR files (JVM)
 * or DEX archives (Android).
 *
 * To create a plugin:
 * 1. Implement this interface.
 * 2. Add a `Plugin-Class: com.example.MyPlugin` entry to MANIFEST.MF.
 * 3. Drop the JAR into the `plugins/` directory next to the fat JAR.
 *
 * Example YAML orchestrator plan extension:
 * ```yaml
 * plugins:
 *   - jar: my-provider-plugin.jar
 * ```
 */
interface AgentPlugin {
    /** Unique plugin identifier (e.g. "my-company/custom-llm"). */
    val id: String

    /** Human-readable version string (e.g. "1.0.0"). */
    val version: String

    /**
     * Called once after the plugin JAR is loaded.
     * Use this to register custom [LlmProvider] implementations,
     * tool executors, or other extensions.
     *
     * @param registry The global provider registry.
     * @param context  A map of configuration key-values from `plugins.yaml`
     *                 or environment variables (e.g. API keys).
     */
    fun onLoad(registry: LlmProviderRegistry, context: Map<String, String>)

    /**
     * Called when the runtime is shutting down.
     * Release any resources (threads, connections) here.
     */
    fun onUnload() {}
}
