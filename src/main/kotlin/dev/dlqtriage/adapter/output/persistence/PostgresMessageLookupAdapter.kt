package dev.dlqtriage.adapter.output.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import dev.dlqtriage.application.port.output.MessageLookupPort
import dev.dlqtriage.domain.ingestion.DlqMessage
import dev.dlqtriage.domain.ingestion.DlqMessageId
import dev.dlqtriage.domain.ingestion.DlqMessageStatus
import dev.dlqtriage.domain.ingestion.PayloadEncoding
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class PostgresMessageLookupAdapter(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) : MessageLookupPort {
    override fun findById(messageId: DlqMessageId): DlqMessage? =
        jdbcTemplate
            .query(
                """
                SELECT id, message_key, source_topic, partition, "offset", payload,
                    payload_encoding, payload_invalid, headers, error_class, error_message,
                    stack_trace, received_at, status
                FROM dlq_message
                WHERE id = ?
                """.trimIndent(),
                { resultSet, _ -> resultSet.toDlqMessage() },
                messageId.value,
            ).firstOrNull()

    private fun ResultSet.toDlqMessage(): DlqMessage {
        val headersJson = getString("headers")
        val headers =
            objectMapper.readValue(
                headersJson,
                object : TypeReference<Map<String, String>>() {},
            )
        return DlqMessage(
            id = DlqMessageId(getObject("id", UUID::class.java)),
            messageKey = getString("message_key"),
            sourceTopic = getString("source_topic"),
            partition = getInt("partition"),
            offset = getLong("offset"),
            payload = getString("payload"),
            payloadEncoding = PayloadEncoding.valueOf(getString("payload_encoding")),
            payloadInvalid = getBoolean("payload_invalid"),
            headers = headers,
            errorClass = getString("error_class"),
            errorMessage = getString("error_message"),
            stackTrace = getString("stack_trace"),
            receivedAt = getTimestamp("received_at").toInstant(),
            status = DlqMessageStatus.valueOf(getString("status")),
        )
    }
}
