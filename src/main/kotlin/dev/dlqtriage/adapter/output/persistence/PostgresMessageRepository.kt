package dev.dlqtriage.adapter.output.persistence

import dev.dlqtriage.application.port.output.MessageRepository
import dev.dlqtriage.application.port.output.SaveMessageResult
import dev.dlqtriage.domain.ingestion.DlqMessage
import dev.dlqtriage.domain.ingestion.DlqMessageId
import org.postgresql.util.PGobject
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.util.UUID

@Repository
class PostgresMessageRepository(
    private val jdbcTemplate: JdbcTemplate,
) : MessageRepository {
    override fun save(message: DlqMessage): SaveMessageResult {
        val headers =
            PGobject().apply {
                type = "jsonb"
                value = headersJson(message.headers)
            }

        var lastException: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val inserted =
                    jdbcTemplate.update(
                        """
                        INSERT INTO dlq_message (
                            id, message_key, source_topic, partition, "offset", payload,
                            payload_encoding, payload_invalid, headers, error_class,
                            error_message, stack_trace, received_at, status
                        ) VALUES (?, ?, ?, ?, ?, ?, ?::varchar, ?, ?::jsonb, ?, ?, ?, ?, ?)
                        ON CONFLICT (source_topic, partition, "offset") DO NOTHING
                        """.trimIndent(),
                        message.id.value,
                        message.messageKey,
                        message.sourceTopic,
                        message.partition,
                        message.offset,
                        message.payload,
                        message.payloadEncoding.name,
                        message.payloadInvalid,
                        headers,
                        message.errorClass,
                        message.errorMessage,
                        message.stackTrace,
                        Timestamp.from(message.receivedAt),
                        message.status.name,
                    )

                if (inserted == 1) {
                    return SaveMessageResult.Saved(message)
                }

                return SaveMessageResult.Duplicate(message.copy(id = persistedMessageId(message)))
            } catch (exception: DataAccessException) {
                lastException = exception
                if (attempt < MAX_ATTEMPTS - 1) {
                    Thread.sleep(BACKOFF_MILLIS * (attempt + 1).toLong())
                }
            }
        }
        throw PersistenceException("Unable to persist DLQ message after $MAX_ATTEMPTS attempts", lastException)
    }

    private fun persistedMessageId(message: DlqMessage): DlqMessageId {
        val persistedId =
            jdbcTemplate.queryForObject(
                """SELECT id FROM dlq_message WHERE source_topic = ? AND partition = ? AND "offset" = ?""",
                UUID::class.java,
                message.sourceTopic,
                message.partition,
                message.offset,
            ) ?: throw PersistenceException("Unable to resolve persisted DLQ message after duplicate insert", null)
        return DlqMessageId(persistedId)
    }

    private fun headersJson(headers: Map<String, String>): String =
        if (headers.isEmpty()) {
            "{}"
        } else {
            headers.entries.joinToString(
                prefix = "{",
                postfix = "}",
                separator = ",",
            ) { (key, value) ->
                "\"${escapeJson(key)}\":\"${escapeJson(value)}\""
            }
        }

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    companion object {
        private const val MAX_ATTEMPTS = 4
        private const val BACKOFF_MILLIS = 100L
    }
}

class PersistenceException(
    message: String,
    cause: Throwable?,
) : RuntimeException(message, cause)
