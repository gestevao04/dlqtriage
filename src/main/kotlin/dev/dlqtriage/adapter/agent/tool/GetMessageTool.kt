package dev.dlqtriage.adapter.agent.tool

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import dev.dlqtriage.application.port.output.AgentToolResult
import dev.dlqtriage.application.port.output.LlmToolDefinition
import dev.dlqtriage.application.port.output.MessageLookupPort
import dev.dlqtriage.application.port.output.ReadOnlyAgentTool
import dev.dlqtriage.domain.ingestion.DlqMessage
import dev.dlqtriage.domain.ingestion.DlqMessageId
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class GetMessageTool(
    private val messageLookupPort: MessageLookupPort,
    private val objectMapper: ObjectMapper,
) : ReadOnlyAgentTool {
    override val definition =
        LlmToolDefinition(
            name = "get_message",
            description = "Read a DLQ message and its failure metadata by message ID.",
            inputSchema =
                """
                {
                  "type": "object",
                  "properties": {"messageId": {"type": "string", "format": "uuid"}},
                  "required": ["messageId"],
                  "additionalProperties": false
                }
                """.trimIndent(),
        )

    override fun execute(argumentsJson: String): AgentToolResult =
        objectMapper
            .parseToolInput(argumentsJson)
            ?.let(::executeInput)
            ?: AgentToolResult.Failure("Arguments must be a JSON object.")

    private fun executeInput(input: ObjectNode): AgentToolResult {
        val messageId =
            input.requiredText("messageId")?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            } ?: return AgentToolResult.Failure("messageId must be a valid UUID.")
        val message = messageLookupPort.findById(DlqMessageId(messageId))
        return message?.let { messageResult(messageId, it) }
            ?: AgentToolResult.Failure("Message was not found.")
    }

    private fun messageResult(
        messageId: UUID,
        message: DlqMessage,
    ): AgentToolResult {
        val result =
            mapOf(
                "messageId" to message.id.value.toString(),
                "messageKey" to message.messageKey,
                "sourceTopic" to message.sourceTopic,
                "partition" to message.partition,
                "offset" to message.offset,
                "payload" to message.payload,
                "payloadEncoding" to message.payloadEncoding.name,
                "payloadInvalid" to message.payloadInvalid,
                "headers" to message.headers,
                "errorClass" to message.errorClass,
                "errorMessage" to message.errorMessage,
                "stackTrace" to message.stackTrace,
                "receivedAt" to message.receivedAt.toString(),
                "status" to message.status.name,
            )
        return AgentToolResult.Success(
            content = objectMapper.writeToolResult(result),
            summary =
                "Read message $messageId from ${message.sourceTopic} with failure metadata.",
        )
    }
}
