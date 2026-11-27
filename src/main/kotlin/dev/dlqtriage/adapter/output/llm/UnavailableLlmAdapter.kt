package dev.dlqtriage.adapter.output.llm

import dev.dlqtriage.application.port.output.LlmCompletionResult
import dev.dlqtriage.application.port.output.LlmMessage
import dev.dlqtriage.application.port.output.LlmPort
import dev.dlqtriage.application.port.output.LlmToolDefinition

class UnavailableLlmAdapter : LlmPort {
    override fun complete(
        messages: List<LlmMessage>,
        tools: List<LlmToolDefinition>,
    ) = LlmCompletionResult.Failure("No LLM adapter is configured for the active profile.")
}
