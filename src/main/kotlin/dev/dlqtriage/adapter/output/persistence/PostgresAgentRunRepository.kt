package dev.dlqtriage.adapter.output.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import dev.dlqtriage.application.port.output.AgentRunRepository
import dev.dlqtriage.domain.agent.AgentInvestigation
import org.postgresql.util.PGobject
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp

@Repository
class PostgresAgentRunRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) : AgentRunRepository {
    @Transactional
    override fun save(investigation: AgentInvestigation) {
        val run = investigation.run
        val evidence =
            PGobject().apply {
                type = "jsonb"
                value = objectMapper.writeValueAsString(run.decision.evidence)
            }
        jdbcTemplate.update(
            """
            INSERT INTO agent_run (
                id, message_id, prompt_version, status, classification, confidence,
                evidence, suggested_action, reasoning_summary, failure_reason,
                started_at, completed_at, input_tokens, output_tokens
            ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            run.id,
            run.messageId.value,
            run.promptVersion,
            run.status.name,
            run.decision.classification.name,
            run.decision.confidence,
            evidence,
            run.decision.suggestedAction.name,
            run.decision.reasoningSummary,
            run.decision.failureReason,
            Timestamp.from(run.startedAt),
            Timestamp.from(run.completedAt),
            run.inputTokens,
            run.outputTokens,
        )
        investigation.steps.forEach { step ->
            jdbcTemplate.update(
                """
                INSERT INTO agent_step (
                    run_id, sequence, summarized_input, tool_called, summarized_result,
                    input_tokens, output_tokens, duration_millis
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                step.runId,
                step.sequence,
                step.summarizedInput,
                step.toolCalled,
                step.summarizedResult,
                step.inputTokens,
                step.outputTokens,
                step.durationMillis,
            )
        }
    }
}
