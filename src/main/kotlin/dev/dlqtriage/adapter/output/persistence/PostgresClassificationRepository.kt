package dev.dlqtriage.adapter.output.persistence

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.dlqtriage.application.port.output.ClassificationRepository
import dev.dlqtriage.domain.classification.Classification
import dev.dlqtriage.domain.ingestion.DlqMessageId
import org.postgresql.util.PGobject
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class PostgresClassificationRepository(
    private val jdbcTemplate: JdbcTemplate,
) : ClassificationRepository {
    override fun save(
        messageId: DlqMessageId,
        classification: Classification,
    ) {
        val evidence =
            PGobject().apply {
                type = "jsonb"
                value = objectMapper.writeValueAsString(classification.evidence)
            }

        jdbcTemplate.update(
            """
            INSERT INTO classification (
                message_id, class, confidence, evidence, classifier_version
            ) VALUES (?, ?, ?, ?::jsonb, ?)
            ON CONFLICT (message_id) DO UPDATE SET
                class = EXCLUDED.class,
                confidence = EXCLUDED.confidence,
                evidence = EXCLUDED.evidence,
                classifier_version = EXCLUDED.classifier_version,
                classified_at = NOW()
            WHERE classification.classifier_version <> EXCLUDED.classifier_version
            """.trimIndent(),
            messageId.value,
            classification.failureClass.name,
            classification.confidence,
            evidence,
            classification.classifierVersion,
        )
    }

    companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}
