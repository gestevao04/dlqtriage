package dev.dlqtriage.adapter.output.persistence

import dev.dlqtriage.application.port.output.MessageRepository
import dev.dlqtriage.application.port.output.SaveMessageResult
import dev.dlqtriage.domain.ingestion.DlqMessage
import org.postgresql.util.PGobject
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp

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

                return if (inserted == 1) {
                    SaveMessageResult.Saved(message)
                } else {
                    SaveMessageResult.Duplicate(message)
                }
            } catch (exception: DataAccessException) {
                lastException = exception
                if (attempt < MAX_ATTEMPTS - 1) {
                    Thread.sleep(BACKOFF_MILLIS * (attempt + 1).toLong())
                }
            }
        }
        throw PersistenceException("Unable to persist DLQ message after $MAX_ATTEMPTS attempts", lastException)
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
