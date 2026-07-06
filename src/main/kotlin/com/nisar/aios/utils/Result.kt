package com.nisar.aios.utils

/**
 * Type-safe result wrapper for handling success and error cases.
 *
 * Provides a functional alternative to exceptions for error handling.
 * All tool executions return Result<T> to ensure clean error propagation.
 *
 * Usage:
 * ```kotlin
 * val result: Result<String> = tool.execute(...)
 * when (result) {
 *     is Result.Success -> println("Result: ${result.value}")
 *     is Result.Error -> println("Error: ${result.error}")
 * }
 * ```
 *
 * @param T The type of the success value
 */
sealed class Result<out T> {

    /**
     * Represents a successful execution.
     *
     * @param value The result value
     */
    data class Success<T>(val value: T) : Result<T>()

    /**
     * Represents a failed execution.
     *
     * @param error Human-readable error message
     */
    data class Error(val error: String) : Result<Nothing>()

    /**
     * Applies a transformation function to the success value.
     *
     * If this is a Success, applies the function and returns the new Result.
     * If this is an Error, returns the error unchanged.
     *
     * @param transform Function to apply to success value
     * @return Transformed result
     */
    inline fun <R> map(transform: (T) -> R): Result<R> =
        when (this) {
            is Success -> try {
                Success(transform(value))
            } catch (e: Exception) {
                Error("Transformation failed: ${e.message}")
            }
            is Error -> this as Result.Error
        }

    /**
     * Chains multiple Result-returning operations.
     *
     * Useful for sequential operations where each depends on the previous result.
     *
     * @param transform Function that takes the success value and returns a new Result
     * @return Chained result
     */
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> =
        when (this) {
            is Success -> try {
                transform(value)
            } catch (e: Exception) {
                Error("FlatMap failed: ${e.message}")
            }
            is Error -> this as Result.Error
        }

    /**
     * Provides a fallback value for errors.
     *
     * @param fallback Value to use if this is an Error
     * @return Success value or fallback
     */
    fun getOrElse(fallback: T): T =
        when (this) {
            is Success -> value
            is Error -> fallback
        }

    /**
     * Provides a fallback Result for errors.
     *
     * @param fallback Result to use if this is an Error
     * @return This result if Success, fallback if Error
     */
    fun orElse(fallback: Result<T>): Result<T> =
        when (this) {
            is Success -> this
            is Error -> fallback
        }

    /**
     * Checks if this is a success.
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * Checks if this is an error.
     */
    fun isError(): Boolean = this is Error

    /**
     * Executes a side effect if this is an error.
     *
     * @param action Action to execute
     * @return This result (for chaining)
     */
    inline fun onError(action: (error: String) -> Unit): Result<T> {
        if (this is Error) {
            action(error)
        }
        return this
    }

    /**
     * Executes a side effect if this is a success.
     *
     * @param action Action to execute
     * @return This result (for chaining)
     */
    inline fun onSuccess(action: (value: T) -> Unit): Result<T> {
        if (this is Success) {
            action(value)
        }
        return this
    }

    companion object {

        /**
         * Creates a successful Result.
         */
        fun <T> success(value: T): Result<T> =
            Success(value)

        /**
         * Creates a failed Result with an error message.
         */
        fun <T> error(message: String): Result<T> =
            Error(message)

        /**
         * Wraps a potentially throwing block in a Result.
         *
         * If the block throws an exception, returns an Error.
         * Otherwise returns the block's result wrapped in Success.
         *
         * @param block Code block that might throw
         * @return Result wrapping the block's output or error
         */
        inline fun <T> attempt(block: () -> T): Result<T> =
            try {
                Success(block())
            } catch (e: Exception) {
                Error("Execution failed: ${e.message}")
            }
    }
}
