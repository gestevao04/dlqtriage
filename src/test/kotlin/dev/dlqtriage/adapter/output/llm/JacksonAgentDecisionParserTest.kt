package dev.dlqtriage.adapter.output.llm

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.dlqtriage.application.port.output.AgentDecisionParseResult
import dev.dlqtriage.domain.classification.ExpectedAction
import dev.dlqtriage.domain.classification.FailureClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JacksonAgentDecisionParserTest {
    private val parser = JacksonAgentDecisionParser(jacksonObjectMapper())

    @Test
    fun parsesAValidStructuredDecision() {
        val result =
            parser.parse(
                """
                {
                  "classification": "TRANSIENT_INFRASTRUCTURE",
                  "confidence": 0.91,
                  "evidence": ["SocketTimeoutException"],
                  "suggested_action": "REPROCESS",
                  "reasoning_summary": "The dependency timed out."
                }
                """.trimIndent(),
            )

        assertThat(result).isInstanceOf(AgentDecisionParseResult.Valid::class.java)
        val decision = (result as AgentDecisionParseResult.Valid).decision
        assertThat(decision.classification).isEqualTo(FailureClass.TRANSIENT_INFRASTRUCTURE)
        assertThat(decision.confidence).isEqualTo(0.91)
        assertThat(decision.evidence).containsExactly("SocketTimeoutException")
        assertThat(decision.suggestedAction).isEqualTo(ExpectedAction.REPROCESS)
    }

    @Test
    fun rejectsMalformedOffSchemaAndUnsafeOutput() {
        val outputs =
            listOf(
                "not json",
                """{"classification":"UNKNOWN"}""",
                """
                {
                  "classification": "UNKNOWN",
                  "confidence": 1.1,
                  "evidence": [],
                  "suggested_action": "ESCALATE",
                  "reasoning_summary": "Unknown"
                }
                """.trimIndent(),
                """
                {
                  "classification": "UNKNOWN",
                  "confidence": 0.5,
                  "evidence": [],
                  "suggested_action": "REPROCESS",
                  "reasoning_summary": "Unknown"
                }
                """.trimIndent(),
                """
                {
                  "classification": "UNKNOWN",
                  "confidence": 0.5,
                  "evidence": [],
                  "suggested_action": "ESCALATE",
                  "reasoning_summary": "Unknown",
                  "extra": true
                }
                """.trimIndent(),
            )

        outputs.forEach { output ->
            assertThat(parser.parse(output)).isInstanceOf(AgentDecisionParseResult.Invalid::class.java)
        }
    }
}
