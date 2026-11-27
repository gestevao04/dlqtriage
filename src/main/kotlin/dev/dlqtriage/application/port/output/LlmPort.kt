package dev.dlqtriage.application.port.output

enum class LlmRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}

data class LlmToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class LlmMessage(
    val role: LlmRole,
    val content: String,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
)

data class LlmToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: String,
)

sealed interface LlmCompletionResult {
    data class Success(
        val content: String,
        val toolCalls: List<LlmToolCall>,
        val inputTokens: Int,
        val outputTokens: Int,
    ) : LlmCompletionResult

    data class Failure(
        val reason: String,
    ) : LlmCompletionResult
}

fun interface LlmPort {
    fun complete(
        messages: List<LlmMessage>,
        tools: List<LlmToolDefinition>,
    ): LlmCompletionResult
}
