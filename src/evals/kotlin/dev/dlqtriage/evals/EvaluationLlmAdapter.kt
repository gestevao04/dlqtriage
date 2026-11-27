package dev.dlqtriage.evals

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import dev.dlqtriage.application.classification.RuleBasedClassifier
import dev.dlqtriage.application.port.output.LlmCompletionResult
import dev.dlqtriage.application.port.output.LlmMessage
import dev.dlqtriage.application.port.output.LlmPort
import dev.dlqtriage.application.port.output.LlmRole
import dev.dlqtriage.application.port.output.LlmToolDefinition
import dev.dlqtriage.domain.ingestion.DlqMessage
import dev.dlqtriage.domain.ingestion.DlqMessageId
import dev.dlqtriage.domain.ingestion.DlqMessageStatus
import dev.dlqtriage.domain.ingestion.PayloadEncoding
import java.time.Instant
import java.util.UUID

class EvaluationLlmAdapter(
    private val classifier: RuleBasedClassifier,
    private val objectMapper: ObjectMapper,
) : LlmPort {
    override fun complete(
        messages: List<LlmMessage>,
        tools: List<LlmToolDefinition>,
    ): LlmCompletionResult {
        val message =
            decodeMessage(messages)
                ?: return LlmCompletionResult.Failure("Evaluation prompt is missing valid delimited message data.")
        val classification = classifier.classify(message)
        val output =
            mapOf(
                "classification" to classification.failureClass.name,
                "confidence" to classification.confidence,
                "evidence" to classification.evidence,
                "suggested_action" to classification.failureClass.expectedAction.name,
                "reasoning_summary" to "Deterministic fake LLM used the structured failure metadata.",
            )
        return LlmCompletionResult.Success(
            content = objectMapper.writeValueAsString(output),
            toolCalls = emptyList(),
            inputTokens = 0,
            outputTokens = 0,
        )
    }

    private fun decodeMessage(messages: List<LlmMessage>): DlqMessage? =
        messages
            .lastOrNull { it.role == LlmRole.USER }
            ?.content
            ?.extractDelimitedData()
            ?.let(::parseMessage)

    private fun String.extractDelimitedData(): String? =
        substringAfter("<UNTRUSTED_DATA>\n", missingDelimiterValue = "")
            .substringBefore("\n</UNTRUSTED_DATA>", missingDelimiterValue = "")
            .takeIf(String::isNotBlank)

    private fun parseMessage(data: String): DlqMessage? =
        parseMessageData(data)?.let { messageData ->
            parseMessageId(messageData)?.let { messageId ->
                DlqMessage(
                    id = DlqMessageId(messageId),
                    messageKey = null,
                    sourceTopic = "eval",
                    partition = 0,
                    offset = 0,
                    payload = "",
                    payloadEncoding = PayloadEncoding.TEXT,
                    payloadInvalid = false,
                    headers = emptyMap(),
                    errorClass = messageData.path("errorClass").asText("UNKNOWN"),
                    errorMessage = messageData.path("errorMessage").takeUnless { it.isNull }?.asText(),
                    stackTrace = messageData.path("stackTrace").takeUnless { it.isNull }?.asText(),
                    receivedAt = Instant.EPOCH,
                    status = DlqMessageStatus.RECEIVED,
                )
            }
        }

    private fun parseMessageData(data: String) =
        try {
            objectMapper.readTree(data)
        } catch (_: JsonProcessingException) {
            null
        }

    private fun parseMessageId(messageData: com.fasterxml.jackson.databind.JsonNode) =
        try {
            UUID.fromString(messageData.path("messageId").asText())
        } catch (_: IllegalArgumentException) {
            null
        }
}
