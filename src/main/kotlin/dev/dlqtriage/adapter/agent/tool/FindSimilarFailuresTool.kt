package dev.dlqtriage.adapter.agent.tool

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import dev.dlqtriage.application.port.output.AgentToolResult
import dev.dlqtriage.application.port.output.LlmToolDefinition
import dev.dlqtriage.application.port.output.ReadOnlyAgentTool
import dev.dlqtriage.application.port.output.SimilarFailureQuery
import dev.dlqtriage.application.port.output.SimilarFailureSearchPort
import dev.dlqtriage.domain.ingestion.DlqMessageId
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class FindSimilarFailuresTool(
    private val similarFailureSearchPort: SimilarFailureSearchPort,
    private val objectMapper: ObjectMapper,
) : ReadOnlyAgentTool {
    override val definition =
        LlmToolDefinition(
            name = "find_similar_failures",
            description = "Search prior failures with the same exception class or similar error text.",
            inputSchema =
                """
                {
                  "type": "object",
                  "properties": {
                    "messageId": {"type": "string", "format": "uuid"},
                    "errorClass": {"type": "string"},
                    "errorMessage": {"type": ["string", "null"]}
                  },
                  "required": ["messageId", "errorClass", "errorMessage"],
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
        val query =
            input.toSimilarFailureQuery()
                ?: return AgentToolResult.Failure("Arguments do not match the similar-failure query schema.")
        val results =
            similarFailureSearchPort.findSimilar(query)
        return AgentToolResult.Success(
            content = objectMapper.writeToolResult(results),
            summary = "Found ${results.size} similar prior failure(s).",
        )
    }

    private fun ObjectNode.toSimilarFailureQuery(): SimilarFailureQuery? {
        val messageId = requiredText("messageId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val errorClass = requiredText("errorClass")
        val errorMessage = get("errorMessage")
        return if (
            messageId == null ||
            errorClass == null ||
            errorMessage.isInvalidErrorMessage()
        ) {
            null
        } else {
            SimilarFailureQuery(
                messageId = DlqMessageId(messageId),
                errorClass = errorClass,
                errorMessage = errorMessage?.takeIf { it.isTextual }?.textValue(),
            )
        }
    }

    private fun JsonNode?.isInvalidErrorMessage() = this != null && !isNull && !isTextual
}
