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
     * Executes a user command through the AI OS pipeline with rich context.
     *
     * Flow:
     * 1. Parse user input to extract intent
     * 2. Find all matching tools via ToolRegistry
     * 3. Validate parameters across all matching tools
     * 4. Execute tools sequentially (order determined by registration)
     * 5. Aggregate results and handle failures
     *
     * @param userInput The raw user input/command
     * @param params Optional tool-specific parameters
     * @return Result<ExecutionContext> containing outputs, intent, and execution metadata
     */
    suspend fun run(
        userInput: String,
        params: Map<String, Any> = emptyMap()
    ): Result<ExecutionContext> {
        // Step 1: Parse intent from user input
        val intent = router.extractIntent(userInput)

        // Step 2: Find all matching tools
        val matchingTools = router.routeToAllTools(userInput)

        if (matchingTools.isEmpty()) {
            return Result.error(
                "No tool found for intent: '$intent'. " +
                "Available tools: ${registry.listTools().map { it.name }}"
            )
        }

        // Step 3: Execute tools and collect results
        val outputs = mutableListOf<ToolOutput>()
        val errors = mutableListOf<ToolError>()

        for (tool in matchingTools) {
            try {
                // Validate params before execution
                val validation = tool.validateParams(params)
                if (validation is Result.Error) {
                    errors.add(
                        ToolError(
                            toolName = tool.name,
                            message = "Validation failed: ${validation.error}"
                        )
                    )
                    continue
                }

                // Execute tool
                val result = tool.execute(userInput, params)
                when (result) {
                    is Result.Success -> {
                        outputs.add(
                            ToolOutput(
                                toolName = tool.name,
                                output = result.value
                            )
                        )
                    }
                    is Result.Error -> {
                        errors.add(
                            ToolError(
                                toolName = tool.name,
                                message = result.error
                            )
                        )
                        // Trigger cleanup
                        tool.onFailure(params, Exception(result.error))
                    }
                }
            } catch (e: Exception) {
                errors.add(
                    ToolError(
                        toolName = tool.name,
                        message = "Execution failed: ${e.message}",
                        exception = e
                    )
                )
                // Trigger tool-level cleanup
                tool.onFailure(params, e)
            }
        }

        // Step 4: Aggregate results
        val context = ExecutionContext(
            intent = intent,
            inputCommand = userInput,
            toolsExecuted = matchingTools.map { it.name },
            outputs = outputs,
            errors = errors,
            successCount = outputs.size,
            failureCount = errors.size
        )

        // Return success if at least one tool succeeded, error if all failed
        return if (outputs.isNotEmpty()) {
            Result.success(context)
        } else if (errors.isNotEmpty()) {
            Result.error(
                "All tools failed. Errors: ${errors.joinToString("; ") { it.message }}"
            )
        } else {
            Result.error("Unexpected state: no outputs or errors recorded")
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

/**
 * Represents the result of a single tool execution.
 */
data class ToolOutput(
    val toolName: String,
    val output: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents an error from a single tool execution.
 */
data class ToolError(
    val toolName: String,
    val message: String,
    val exception: Exception? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Execution context aggregating all results from a run.
 *
 * Provides complete context for understanding what happened during execution:
 * - What intent was detected
 * - Which tools were invoked
 * - What each tool output
 * - What errors occurred
 * - Overall success/failure counts
 */
data class ExecutionContext(
    val intent: String,
    val inputCommand: String,
    val toolsExecuted: List<String>,
    val outputs: List<ToolOutput>,
    val errors: List<ToolError>,
    val successCount: Int,
    val failureCount: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Check if execution was entirely successful.
     */
    fun isSuccess(): Boolean = errors.isEmpty() && outputs.isNotEmpty()

    /**
     * Check if execution had no results.
     */
    fun isEmpty(): Boolean = outputs.isEmpty() && errors.isEmpty()

    /**
     * Get the primary output (from the first successful tool).
     */
    fun getPrimaryOutput(): String? = outputs.firstOrNull()?.output

    override fun toString(): String =
        "ExecutionContext(intent='$intent', executed=${toolsExecuted.size}, " +
        "success=$successCount, failures=$failureCount)"
}
