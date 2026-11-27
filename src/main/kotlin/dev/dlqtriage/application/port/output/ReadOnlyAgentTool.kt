package dev.dlqtriage.application.port.output

sealed interface AgentToolResult {
    data class Success(
        val content: String,
        val summary: String,
    ) : AgentToolResult

    data class Failure(
        val reason: String,
    ) : AgentToolResult
}

interface ReadOnlyAgentTool {
    val definition: LlmToolDefinition

    fun execute(argumentsJson: String): AgentToolResult
}
