package dev.dlqtriage.adapter.output.persistence

import dev.dlqtriage.application.port.output.SimilarFailure
import dev.dlqtriage.application.port.output.SimilarFailureQuery
import dev.dlqtriage.application.port.output.SimilarFailureSearchPort
import dev.dlqtriage.domain.ingestion.DlqMessageId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class PostgresSimilarFailureSearchAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SimilarFailureSearchPort {
    override fun findSimilar(query: SimilarFailureQuery): List<SimilarFailure> {
        val messagePattern =
            query.errorMessage
                ?.takeIf(String::isNotBlank)
                ?.take(MAX_ERROR_TEXT_LENGTH)
                ?.escapeLikePattern()
                ?.let { "%$it%" }
        return jdbcTemplate.query(
            """
            SELECT m.id, m.error_class, m.error_message, c.class, c.confidence
            FROM dlq_message m
            LEFT JOIN classification c ON c.message_id = m.id
            WHERE m.id <> ?
              AND (
                  m.error_class = ?
                  OR (CAST(? AS TEXT) IS NOT NULL AND m.error_message ILIKE ? ESCAPE '\')
              )
            ORDER BY CASE WHEN m.error_class = ? THEN 0 ELSE 1 END, m.received_at DESC
            LIMIT ?
            """.trimIndent(),
            { resultSet, _ ->
                SimilarFailure(
                    messageId = DlqMessageId(resultSet.getObject("id", UUID::class.java)),
                    errorClass = resultSet.getString("error_class"),
                    errorMessage = resultSet.getString("error_message"),
                    classification = resultSet.getString("class"),
                    confidence = resultSet.getObject("confidence", Double::class.javaObjectType),
                )
            },
            query.messageId.value,
            query.errorClass,
            messagePattern,
            messagePattern,
            query.errorClass,
            MAX_RESULTS,
        )
    }

    private fun String.escapeLikePattern(): String =
        replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    companion object {
        private const val MAX_ERROR_TEXT_LENGTH = 120
        private const val MAX_RESULTS = 5
    }
}
