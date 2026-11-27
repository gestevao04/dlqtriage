package dev.dlqtriage.adapter.output.llm

import dev.dlqtriage.application.port.output.LlmCompletionResult
import dev.dlqtriage.application.port.output.LlmMessage
import dev.dlqtriage.application.port.output.LlmPort
import dev.dlqtriage.application.port.output.LlmToolDefinition
import java.util.concurrent.ConcurrentLinkedQueue

class ProgrammableFakeLlmAdapter : LlmPort {
    private val scriptedResponses = ConcurrentLinkedQueue<LlmCompletionResult>()
    private val observedRequests = ConcurrentLinkedQueue<Pair<List<LlmMessage>, List<LlmToolDefinition>>>()

    val requests: List<Pair<List<LlmMessage>, List<LlmToolDefinition>>>
        get() = observedRequests.toList()

    fun enqueue(result: LlmCompletionResult) {
        scriptedResponses.add(result)
    }

    override fun complete(
        messages: List<LlmMessage>,
        tools: List<LlmToolDefinition>,
    ): LlmCompletionResult {
        observedRequests.add(messages.toList() to tools.toList())
        return scriptedResponses.poll()
            ?: LlmCompletionResult.Failure("No response was configured for the fake LLM.")
    }
}
