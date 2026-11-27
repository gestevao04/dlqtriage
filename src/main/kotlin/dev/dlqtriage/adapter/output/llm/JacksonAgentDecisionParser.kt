package dev.dlqtriage.adapter.output.llm

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.dlqtriage.application.port.output.AgentDecisionParseResult
import dev.dlqtriage.application.port.output.AgentDecisionParserPort
import dev.dlqtriage.domain.agent.AgentDecision
import dev.dlqtriage.domain.classification.ExpectedAction
import dev.dlqtriage.domain.classification.FailureClass

class JacksonAgentDecisionParser(
    private val objectMapper: ObjectMapper,
) : AgentDecisionParserPort {
    override fun parse(output: String): AgentDecisionParseResult {
        val root =
            try {
                objectMapper.readTree(output)
            } catch (_: JsonProcessingException) {
                return AgentDecisionParseResult.Invalid("LLM output is not valid JSON.")
            }
        return validateRoot(root)
    }

    private fun validateRoot(root: JsonNode?): AgentDecisionParseResult =
        when {
            root == null || !root.isObject ->
                AgentDecisionParseResult.Invalid("LLM output must be a JSON object.")
            root.fieldNames().asSequence().toSet() != REQUIRED_FIELDS ->
                AgentDecisionParseResult.Invalid("LLM output does not match the required decision schema.")
            else ->
                DecisionFields.from(root)?.let(::validateFields)
                    ?: AgentDecisionParseResult.Invalid("LLM output contains fields with invalid types.")
        }

    private fun validateFields(fields: DecisionFields): AgentDecisionParseResult {
        val classification = FailureClass.entries.firstOrNull { it.name == fields.classification.textValue() }
        val action = ExpectedAction.entries.firstOrNull { it.name == fields.action.textValue() }
        val confidence = fields.confidence.doubleValue()
        return when {
            classification == null ->
                AgentDecisionParseResult.Invalid("LLM output contains an unknown classification.")
            action == null ->
                AgentDecisionParseResult.Invalid("LLM output contains an unknown suggested action.")
            else ->
                fields
                    .validationError(classification, action, confidence)
                    ?.let(AgentDecisionParseResult::Invalid)
                    ?: AgentDecisionParseResult.Valid(
                        AgentDecision(
                            classification = classification,
                            confidence = confidence,
                            evidence = fields.evidence.map { it.textValue() },
                            suggestedAction = action,
                            reasoningSummary = fields.summary.textValue(),
                        ),
                    )
        }
    }

    private data class DecisionFields(
        val classification: JsonNode,
        val confidence: JsonNode,
        val evidence: JsonNode,
        val action: JsonNode,
        val summary: JsonNode,
    ) {
        fun validationError(
            failureClass: FailureClass,
            action: ExpectedAction,
            confidence: Double,
        ): String? =
            when {
                !confidence.isFinite() || confidence !in 0.0..1.0 ->
                    "LLM confidence must be a number between 0 and 1."
                evidence.any { !it.isTextual } -> "LLM evidence must contain only strings."
                summary.textValue().isBlank() -> "LLM reasoning_summary must not be blank."
                action == ExpectedAction.REPROCESS && failureClass != FailureClass.TRANSIENT_INFRASTRUCTURE ->
                    "REPROCESS is only valid for TRANSIENT_INFRASTRUCTURE."
                else -> null
            }

        companion object {
            fun from(root: JsonNode): DecisionFields? {
                val classification = root.get("classification")
                val confidence = root.get("confidence")
                val evidence = root.get("evidence")
                val action = root.get("suggested_action")
                val summary = root.get("reasoning_summary")
                return when {
                    !classification.isTextual -> null
                    !confidence.isNumber -> null
                    !evidence.isArray -> null
                    !action.isTextual -> null
                    !summary.isTextual -> null
                    else -> DecisionFields(classification, confidence, evidence, action, summary)
                }
            }
        }
    }

    companion object {
        private val REQUIRED_FIELDS =
            setOf("classification", "confidence", "evidence", "suggested_action", "reasoning_summary")
    }
}
