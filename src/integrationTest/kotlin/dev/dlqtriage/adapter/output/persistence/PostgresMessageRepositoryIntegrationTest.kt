package dev.dlqtriage.adapter.output.persistence

import dev.dlqtriage.application.port.output.SaveMessageResult
import dev.dlqtriage.domain.ingestion.DlqMessage
import dev.dlqtriage.domain.ingestion.DlqMessageId
import dev.dlqtriage.domain.ingestion.DlqMessageStatus
import dev.dlqtriage.domain.ingestion.PayloadEncoding
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@Testcontainers
class PostgresMessageRepositoryIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer("postgres:18")
                .withDatabaseName("dlqtriage")
                .withUsername("dlqtriage")
                .withPassword("dlqtriage")

        lateinit var jdbcTemplate: JdbcTemplate

        @BeforeAll
        @JvmStatic
        fun migrate() {
            val dataSource =
                DriverManagerDataSource(
                    postgres.jdbcUrl,
                    postgres.username,
                    postgres.password,
                )
            Flyway
                .configure()
                .dataSource(dataSource)
                .load()
                .migrate()
            jdbcTemplate = JdbcTemplate(dataSource)
        }
    }

    @Test
    fun persistsOnceAndRejectsRedelivery() {
        val repository = PostgresMessageRepository(jdbcTemplate)
        val message =
            DlqMessage(
                id = DlqMessageId(UUID.randomUUID()),
                messageKey = "order-1",
                sourceTopic = "dlq.orders",
                partition = 0,
                offset = System.nanoTime(),
                payload = "{\"orderId\":\"123\"}",
                payloadEncoding = PayloadEncoding.TEXT,
                payloadInvalid = false,
                headers = mapOf("x-original-topic" to "orders"),
                errorClass = "UNKNOWN",
                errorMessage = null,
                stackTrace = null,
                receivedAt = Instant.now(),
                status = DlqMessageStatus.RECEIVED,
            )

        val first = repository.save(message)
        val duplicate = repository.save(message)
        val count =
            jdbcTemplate.queryForObject(
                """SELECT COUNT(*) FROM dlq_message WHERE source_topic = ? AND partition = ? AND "offset" = ?""",
                Int::class.java,
                message.sourceTopic,
                message.partition,
                message.offset,
            )

        assertThat(first).isInstanceOf(SaveMessageResult.Saved::class.java)
        assertThat(duplicate).isInstanceOf(SaveMessageResult.Duplicate::class.java)
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun persistsBinaryPayloadAsBase64() {
        val repository = PostgresMessageRepository(jdbcTemplate)
        val message =
            DlqMessage(
                id = DlqMessageId(UUID.randomUUID()),
                messageKey = null,
                sourceTopic = "dlq.binary",
                partition = 1,
                offset = System.nanoTime(),
                payload = "wyg=",
                payloadEncoding = PayloadEncoding.BASE64,
                payloadInvalid = true,
                headers = emptyMap(),
                errorClass = "UNKNOWN",
                errorMessage = null,
                stackTrace = null,
                receivedAt = Instant.now(),
                status = DlqMessageStatus.RECEIVED,
            )

        val result = repository.save(message)
        val row =
            jdbcTemplate.queryForMap(
                "SELECT payload, payload_encoding, payload_invalid FROM dlq_message WHERE source_topic = ?",
                message.sourceTopic,
            )

        assertThat(result).isInstanceOf(SaveMessageResult.Saved::class.java)
        assertThat(row["payload"]).isEqualTo("wyg=")
        assertThat(row["payload_encoding"]).isEqualTo("BASE64")
        assertThat(row["payload_invalid"]).isEqualTo(true)
    }
}
