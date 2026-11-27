package dev.dlqtriage.adapter.output.llm

import dev.dlqtriage.application.port.output.LlmCompletionResult
import dev.dlqtriage.application.port.output.LlmMessage
import dev.dlqtriage.application.port.output.LlmRole
import dev.dlqtriage.application.port.output.LlmToolDefinition
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaChatOptions
import org.springframework.ai.retry.NonTransientAiException

class SpringAiLlmAdapterTest {
    @Test
    fun sendsReadOnlyToolSchemasAndMapsSpringAiToolCallsToThePort() {
        val model = mockk<OllamaChatModel>()
        val options = OllamaChatOptions.builder().model("qwen3:4b").build()
        val toolCall = AssistantMessage.ToolCall("call-1", "function", "get_schema", """{"schemaName":"order-v1"}""")
        val response =
            ChatResponse(
                listOf(
                    Generation(
                        AssistantMessage
                            .builder()
                            .content("")
                            .toolCalls(listOf(toolCall))
                            .build(),
                    ),
                ),
            )
        val capturedPrompt = slot<Prompt>()
        every { model.options } returns options
        every { model.call(capture(capturedPrompt)) } returns response
        val adapter = SpringAiLlmAdapter(model)

        val result =
            adapter.complete(
                messages =
                    listOf(
                        LlmMessage(LlmRole.SYSTEM, "Read-only."),
                        LlmMessage(LlmRole.USER, "Investigate this message."),
                    ),
                tools =
                    listOf(
                        LlmToolDefinition(
                            name = "get_schema",
                            description = "Read fixture schema.",
                            inputSchema = """{"type":"object","properties":{"schemaName":{"type":"string"}}}""",
                        ),
                    ),
            )

        assertThat(result).isInstanceOf(LlmCompletionResult.Success::class.java)
        val completion = result as LlmCompletionResult.Success
        assertThat(completion.toolCalls).hasSize(1)
        assertThat(completion.toolCalls.single().name).isEqualTo("get_schema")
        val promptOptions = capturedPrompt.captured.options as OllamaChatOptions
        assertThat(promptOptions.toolCallbacks.orEmpty()).hasSize(1)
        val outputSchema = promptOptions.format as Map<*, *>
        assertThat(outputSchema["type"]).isEqualTo("object")
        assertThat(outputSchema["additionalProperties"]).isEqualTo(false)
        assertThat(promptOptions.thinkOption?.toJsonValue()).isEqualTo(false)
        assertThat(
            promptOptions.toolCallbacks
                .orEmpty()
                .single()
                .toolDefinition
                .name(),
        ).isEqualTo("get_schema")
        verify(exactly = 1) { model.call(any<Prompt>()) }
    }

    @Test
    fun convertsSpringAiFailuresIntoExplicitLlmFailures() {
        val model = mockk<OllamaChatModel>()
        every { model.options } returns OllamaChatOptions.builder().model("qwen3:4b").build()
        every { model.call(any<Prompt>()) } throws NonTransientAiException("model unavailable")

        val result = SpringAiLlmAdapter(model).complete(emptyList(), emptyList())

        assertThat(result)
            .isEqualTo(LlmCompletionResult.Failure("Ollama request failed: NonTransientAiException."))
    }
}
