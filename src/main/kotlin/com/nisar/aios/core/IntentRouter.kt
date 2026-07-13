package com.nisar.aios.core

import com.nisar.aios.utils.Result

/**
 * Extracts and routes user intents to appropriate tools.
 *
 * The IntentRouter is responsible for:
 * - Parsing user input into structured intents
 * - Routing intents to matching tools via the ToolRegistry
 * - Handling cases where no tool matches the intent
 * - Logging and debugging intent resolution
 *
 * This layer provides clean separation between user input parsing and tool execution.
 *
 * Current implementation uses simple heuristics (lowercase normalization).
 * Production deployments can integrate:
 * - LiteRT-LM for semantic understanding
 * - Custom NLU models
 * - Regex patterns for domain-specific commands
 *
 * @param registry The tool registry used for routing
 * @see BaseTool
 * @see ToolRegistry
 * @see NisarAIOS
 */
class IntentRouter(private val registry: ToolRegistry) {

    /**
     * Parses a user message to extract the primary intent.
     *
     * For now, uses simple heuristics:
     * - Lowercase the input
     * - Trim whitespace
     *
     * In production, this could integrate with LiteRT-LM or a dedicated NLU service.
     *
     * @param userMessage The raw user input
     * @return The extracted intent string
     */
    fun extractIntent(userMessage: String): String {
        val normalized = userMessage.trim().lowercase()
        return if (normalized.isEmpty()) "unknown" else normalized
    }

    /**
     * Finds the tool for a given user message.
     *
     * Flow:
     * 1. Extract intent from user message
     * 2. Query ToolRegistry to find a matching tool
     * 3. Return tool or null if not found
     *
     * @param userMessage The raw user input
     * @return The matching tool, or null if no tool can handle the intent
     */
    fun routeToTool(userMessage: String): BaseTool? {
        val intent = extractIntent(userMessage)
        return registry.findTool(intent)
    }

    /**
     * Routes a user message and returns all candidate tools.
     *
     * Useful for debugging or when multiple tools could handle the same intent.
     *
     * @param userMessage The raw user input
     * @return List of all tools that can handle this intent
     */
    fun routeToAllTools(userMessage: String): List<BaseTool> {
        val intent = extractIntent(userMessage)
        return registry.findAllTools(intent)
    }

    /**
     * Validates whether a user message can be routed to at least one tool.
     *
     * @param userMessage The raw user input
     * @return true if at least one tool can handle this intent
     */
    fun canRoute(userMessage: String): Boolean =
        routeToTool(userMessage) != null

    override fun toString(): String =
        "IntentRouter(registry=$registry)"
}
