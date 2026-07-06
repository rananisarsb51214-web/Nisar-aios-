package com.nisar.aios.core

import com.nisar.aios.utils.Result

/**
 * Main orchestration kernel for the Nisar AI OS.
 *
 * NisarAIOS is the central execution engine that coordinates:
 * - Intent routing (via IntentRouter)
 * - Tool discovery and execution (via ToolRegistry)
 * - Error handling and recovery
 * - Execution tracing and logging
 *
 * Architecture:
 * User Input → IntentRouter → ToolRegistry → Tool.execute() → Result
 *
 * Key principles:
 * - Stateless orchestration (all state is in tools)
 * - Deterministic execution (same input → same output)
 * - Clean error handling (all failures wrapped in Result)
 * - Testable in isolation (no hidden side effects)
 *
 * @param registry The tool registry for tool management
 * @see BaseTool
 * @see ToolRegistry
 * @see IntentRouter
 */
class NisarAIOS(
    private val registry: ToolRegistry
) {

    private val router = IntentRouter(registry)

    /**
     * Executes a user command through the AI OS pipeline.
     *
     * Flow:
     * 1. Route user input to the appropriate tool
     * 2. If tool found: validate params → execute → handle result
     * 3. If no tool found: return a "no matching tool" error
     * 4. All results wrapped in Result<String> for safe error handling
     *
     * @param userInput The raw user input/command
     * @param params Optional tool-specific parameters
     * @return Result<String> containing either the execution result or error details
     */
    suspend fun execute(
        userInput: String,
        params: Map<String, Any> = emptyMap()
    ): Result<String> {
        // Step 1: Route to tool
        val tool = router.routeToTool(userInput)
            ?: return Result.error(
                "No matching tool found for intent: '$userInput'. " +
                "Available tools: ${registry.listTools().map { it.name }}"
            )

        // Step 2: Validate parameters
        val validation = tool.validateParams(params)
        if (validation is Result.Error) {
            return Result.error("Parameter validation failed: ${validation.error}")
        }

        // Step 3: Execute tool
        return try {
            val result = tool.execute(userInput, params)
            result
        } catch (e: Exception) {
            // Step 4: Handle failure and cleanup
            tool.onFailure(params, e)
            Result.error(
                "Tool '${tool.name}' execution failed: ${e.message}. " +
                "Cause: ${e.cause?.message ?: "unknown"}"
            )
        }
    }

    /**
     * Executes a user command with explicit tool name.
     *
     * Useful when you know exactly which tool to use (e.g., from routing logic elsewhere).
     *
     * @param toolName The exact name of the tool to execute
     * @param userInput The user input/command
     * @param params Tool-specific parameters
     * @return Result<String> containing either the execution result or error details
     */
    suspend fun executeWithTool(
        toolName: String,
        userInput: String,
        params: Map<String, Any> = emptyMap()
    ): Result<String> {
        val tool = registry.getTool(toolName)
            ?: return Result.error(
                "Tool '$toolName' not found. " +
                "Available tools: ${registry.listTools().map { it.name }}"
            )

        return try {
            val validation = tool.validateParams(params)
            if (validation is Result.Error) {
                return Result.error("Parameter validation failed: ${validation.error}")
            }
            tool.execute(userInput, params)
        } catch (e: Exception) {
            tool.onFailure(params, e)
            Result.error(
                "Tool '$toolName' execution failed: ${e.message}. " +
                "Cause: ${e.cause?.message ?: "unknown"}"
            )
        }
    }

    /**
     * Checks if the AI OS can handle the given user input.
     *
     * @param userInput The user input to check
     * @return true if at least one tool can handle this input
     */
    fun canHandle(userInput: String): Boolean =
        router.canRoute(userInput)

    /**
     * Returns all tools that could handle the given user input.
     *
     * Useful for debugging and understanding routing decisions.
     *
     * @param userInput The user input
     * @return List of candidate tools
     */
    fun getCandidateTools(userInput: String): List<BaseTool> =
        router.routeToAllTools(userInput)

    /**
     * Lists all registered tools.
     *
     * @return Immutable list of tools in registration order
     */
    fun listTools(): List<BaseTool> =
        registry.listTools()

    /**
     * Returns the number of registered tools.
     */
    fun toolCount(): Int =
        registry.toolCount()

    override fun toString(): String =
        "NisarAIOS(tools=${registry.toolCount()}, registry=$registry)"
}
