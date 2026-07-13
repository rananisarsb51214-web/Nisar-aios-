package com.nisar.aios.core

import com.nisar.aios.utils.Result

/**
 * Base abstraction for all tools in the Nisar AI OS.
 *
 * Every tool must implement this interface to be registered with the system.
 * Tools are stateless, deterministic, and testable in isolation.
 *
 * @see ToolRegistry
 * @see NisarAIOS
 */
abstract class BaseTool {

    /**
     * Unique identifier for this tool.
     * Must be lowercase, alphanumeric, and use underscores (e.g., "terminal_tool", "file_system_tool")
     */
    abstract val name: String

    /**
     * Human-readable description of what this tool does.
     * Used for logging, debugging, and documentation.
     */
    abstract val description: String

    /**
     * Determines whether this tool can handle the given intent.
     *
     * This method is called by the ToolRegistry to find the appropriate tool.
     * Implementation should be fast and side-effect-free.
     *
     * @param intent The user intent or command to check
     * @return true if this tool can handle the intent, false otherwise
     */
    abstract fun canHandle(intent: String): Boolean

    /**
     * Executes the tool with the given intent and parameters.
     *
     * This is the main entry point for tool execution. Implementation must:
     * - Be deterministic (same input → same output)
     * - Not modify global state
     * - Handle all error cases gracefully
     * - Return a Result wrapping either success or failure
     *
     * @param intent The user intent or command to execute
     * @param params Tool-specific parameters passed by the orchestrator
     * @return Result<String> containing either the tool output or an error
     */
    abstract suspend fun execute(intent: String, params: Map<String, Any>): Result<String>

    /**
     * Optional: Validate parameters before execution.
     *
     * Override this method to add parameter validation logic.
     * Called before execute() to fail fast on invalid inputs.
     *
     * @param params Parameters to validate
     * @return Result<Unit> with error if validation fails
     */
    open fun validateParams(params: Map<String, Any>): Result<Unit> =
        Result.success(Unit)

    /**
     * Optional: Cleanup or rollback logic after execution.
     *
     * Called when execution fails or is interrupted.
     * Useful for cleaning up temporary resources or state.
     *
     * @param params Original parameters
     * @param error The error that occurred
     */
    open suspend fun onFailure(params: Map<String, Any>, error: Throwable) {
        // Default: no-op. Override to implement cleanup.
    }

    override fun toString(): String = "Tool(name='$name', description='$description')"
}
