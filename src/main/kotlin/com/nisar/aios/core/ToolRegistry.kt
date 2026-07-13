package com.nisar.aios.core

import com.nisar.aios.utils.Result

/**
 * Stateless registry for managing and discovering tools.
 *
 * The ToolRegistry is the central authority for tool management:
 * - Registers tools at startup
 * - Routes intents to appropriate tools
 * - Maintains no state other than the tool list
 * - Provides introspection for debugging and documentation
 *
 * Thread-safe after initialization (tools list should not be modified concurrently after startup).
 *
 * @see BaseTool
 * @see NisarAIOS
 */
class ToolRegistry {

    private val tools = mutableListOf<BaseTool>()

    /**
     * Registers a new tool with the registry.
     *
     * Should be called during system initialization, before execution begins.
     * Tools should be registered in priority order (higher priority first).
     *
     * @param tool The tool to register
     * @throws IllegalArgumentException if a tool with the same name is already registered
     */
    fun register(tool: BaseTool) {
        val existingName = tools.find { it.name == tool.name }
        if (existingName != null) {
            throw IllegalArgumentException(
                "Tool with name '${tool.name}' is already registered. " +
                "Tool names must be unique."
            )
        }
        tools.add(tool)
    }

    /**
     * Registers multiple tools at once.
     *
     * @param toolsList List of tools to register
     * @throws IllegalArgumentException if any tool name conflicts with existing tools
     */
    fun registerAll(toolsList: List<BaseTool>) {
        for (tool in toolsList) {
            register(tool)
        }
    }

    /**
     * Finds the first tool that can handle the given intent.
     *
     * Uses the first-match strategy: returns the first tool whose canHandle() returns true.
     * Order of registration matters—tools registered earlier have higher priority.
     *
     * @param intent The user intent or command
     * @return The matching tool, or null if no tool can handle this intent
     */
    fun findTool(intent: String): BaseTool? =
        tools.firstOrNull { it.canHandle(intent) }

    /**
     * Finds all tools that can handle the given intent.
     *
     * Useful for debugging or when multiple tools could handle the same intent.
     *
     * @param intent The user intent or command
     * @return List of all matching tools
     */
    fun findAllTools(intent: String): List<BaseTool> =
        tools.filter { it.canHandle(intent) }

    /**
     * Retrieves a tool by its exact name.
     *
     * @param name The tool name to look up
     * @return The tool with the given name, or null if not found
     */
    fun getTool(name: String): BaseTool? =
        tools.find { it.name == name }

    /**
     * Lists all registered tools.
     *
     * Useful for introspection, logging, and help documentation.
     *
     * @return Immutable list of all registered tools in registration order
     */
    fun listTools(): List<BaseTool> =
        tools.toList() // Return immutable copy

    /**
     * Checks if a tool is registered by name.
     *
     * @param name The tool name
     * @return true if a tool with that name is registered
     */
    fun hasTool(name: String): Boolean =
        tools.any { it.name == name }

    /**
     * Returns the number of registered tools.
     */
    fun toolCount(): Int =
        tools.size

    /**
     * Clears all registered tools.
     *
     * WARNING: Use only for testing or when resetting the system state.
     * Called automatically in unit tests to ensure isolation.
     */
    fun clear() {
        tools.clear()
    }

    override fun toString(): String =
        "ToolRegistry(count=${tools.size}, tools=${tools.map { it.name }})"
}
