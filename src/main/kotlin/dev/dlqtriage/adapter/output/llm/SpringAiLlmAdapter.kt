package dev.dlqtriage.adapter.output.llm

import com.fasterxml.jackson.databind.JsonNode
import dev.dlqtriage.application.port.output.LlmCompletionResult
import dev.dlqtriage.application.port.output.LlmMessage
import dev.dlqtriage.application.port.output.LlmPort
import dev.dlqtriage.application.port.output.LlmRole
import dev.dlqtriage.application.port.output.LlmToolDefinition
import dev.dlqtriage.domain.classification.ExpectedAction
import dev.dlqtriage.domain.classification.FailureClass
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.retry.NonTransientAiException
import org.springframework.ai.retry.TransientAiException
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.function.FunctionToolCallback
import org.springframework.web.client.RestClientException
import java.util.function.Function

class SpringAiLlmAdapter(
    private val chatModel: OllamaChatModel,
) : LlmPort {
    override fun complete(
        messages: List<LlmMessage>,
        tools: List<LlmToolDefinition>,
    ): LlmCompletionResult {
        return try {
            val callbacks = tools.map(::toolCallback)
            val options =
                chatModel.options
                    .mutate()
                    .toolCallbacks(callbacks)
                    .disableThinking()
                    .format(DECISION_OUTPUT_SCHEMA)
                    .build()
            val response =
                chatModel.call(
                    Prompt(
                        messages.map(::toSpringMessage),
                        options,
                    ),
                )
            val generation =
                response.result
                    ?: return LlmCompletionResult.Failure("Ollama returned no completion.")
            val output = generation.output
            val usage = response.metadata.usage
            LlmCompletionResult.Success(
                content = output.text.orEmpty(),
                toolCalls =
                    output.toolCalls.map { call ->
                        dev.dlqtriage.application.port.output.LlmToolCall(
                            id = call.id(),
                            name = call.name(),
                            arguments = call.arguments(),
                        )
                    },
                inputTokens = usage.promptTokens.coerceAtLeast(0),
                outputTokens = usage.completionTokens.coerceAtLeast(0),
            )
        } catch (exception: RestClientException) {
            failure(exception)
        } catch (exception: NonTransientAiException) {
            failure(exception)
        } catch (exception: TransientAiException) {
            failure(exception)
        } catch (exception: IllegalStateException) {
            logger.warn("Ollama response could not be processed: {}", exception::class.simpleName)
            LlmCompletionResult.Failure("Ollama response could not be processed.")
        }
    }

    private fun failure(exception: RuntimeException): LlmCompletionResult.Failure {
        logger.warn("Ollama request failed: {}", exception::class.simpleName)
        return LlmCompletionResult.Failure("Ollama request failed: ${exception::class.simpleName}.")
    }

    private fun toSpringMessage(message: LlmMessage) =
        when (message.role) {
            LlmRole.SYSTEM -> SystemMessage(message.content)
            LlmRole.USER -> UserMessage(message.content)
            LlmRole.ASSISTANT ->
                AssistantMessage
                    .builder()
                    .content(message.content)
                    .toolCalls(
                        message.toolCalls.map { call ->
                            AssistantMessage.ToolCall(call.id, "function", call.name, call.arguments)
                        },
                    ).build()

            LlmRole.TOOL ->
                ToolResponseMessage
                    .builder()
                    .responses(
                        listOf(
                            ToolResponseMessage.ToolResponse(
                                message.toolCallId.orEmpty(),
                                message.toolName.orEmpty(),
                                message.content,
                            ),
                        ),
                    ).build()
        }

    private fun toolCallback(tool: LlmToolDefinition): ToolCallback =
        FunctionToolCallback
            .builder<JsonNode, String>(
                tool.name,
                Function { """{"message":"Use the investigator's read-only tool loop."}""" },
            ).description(tool.description)
            .inputType(JsonNode::class.java)
            .inputSchema(tool.inputSchema)
            .build()

    companion object {
        private val DECISION_OUTPUT_SCHEMA =
            mapOf(
                "type" to "object",
                "properties" to
                    mapOf(
                        "classification" to
                            mapOf(
                                "type" to "string",
                                "enum" to FailureClass.entries.map { it.name },
                            ),
                        "confidence" to mapOf("type" to "number", "minimum" to 0, "maximum" to 1),
                        "evidence" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                        "suggested_action" to
                            mapOf(
                                "type" to "string",
                                "enum" to ExpectedAction.entries.map { it.name },
                            ),
                        "reasoning_summary" to mapOf("type" to "string", "minLength" to 1),
                    ),
                "required" to
                    listOf("classification", "confidence", "evidence", "suggested_action", "reasoning_summary"),
                "additionalProperties" to false,
            )
        private val logger = LoggerFactory.getLogger(SpringAiLlmAdapter::class.java)
    }
}
