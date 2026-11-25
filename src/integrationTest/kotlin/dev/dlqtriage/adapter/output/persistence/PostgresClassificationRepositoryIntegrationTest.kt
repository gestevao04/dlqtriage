package dev.dlqtriage.adapter.output.persistence

import dev.dlqtriage.application.classification.RuleBasedClassifier
import dev.dlqtriage.application.port.output.SaveMessageResult
import dev.dlqtriage.domain.classification.FailureClass
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
class PostgresClassificationRepositoryIntegrationTest {
    companion object {
        private const val ERROR_CLASS_CONFIDENCE = 0.99

        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer("postgres:18")
                .withDatabaseName("dlqtriage")
                .withUsername("dlqtriage")
                .withPassword("dlqtriage")

        private lateinit var jdbcTemplate: JdbcTemplate

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
    fun persistsClassificationFieldsAndDoesNotDuplicateOnRetry() {
        val message = testMessage()
        val messageRepository = PostgresMessageRepository(jdbcTemplate)
        assertThat(messageRepository.save(message)).isInstanceOf(SaveMessageResult.Saved::class.java)

        val classification = RuleBasedClassifier().classify(message)
        val repository = PostgresClassificationRepository(jdbcTemplate)
        repository.save(message.id, classification)
        repository.save(message.id, classification)

        val row =
            jdbcTemplate.queryForMap(
                "SELECT class, confidence, evidence, classifier_version FROM classification WHERE message_id = ?",
                message.id.value,
            )

        assertThat(row["class"]).isEqualTo(FailureClass.TRANSIENT_INFRASTRUCTURE.name)
        assertThat(row["confidence"]).isEqualTo(ERROR_CLASS_CONFIDENCE)
        assertThat(row["evidence"].toString()).contains("SocketTimeoutException")
        assertThat(row["classifier_version"]).isEqualTo(RuleBasedClassifier.VERSION)
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM classification WHERE message_id = ?",
                Int::class.java,
                message.id.value,
            ),
        ).isEqualTo(1)
    }

    @Test
    fun updatesStoredResultWhenClassifierVersionChanges() {
        val message = testMessage()
        PostgresMessageRepository(jdbcTemplate).save(message)
        val repository = PostgresClassificationRepository(jdbcTemplate)
        repository.save(message.id, RuleBasedClassifier().classify(message))
        repository.save(
            message.id,
            RuleBasedClassifier().classify(message).copy(classifierVersion = "baseline-v2"),
        )

        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT classifier_version FROM classification WHERE message_id = ?",
                String::class.java,
                message.id.value,
            ),
        ).isEqualTo("baseline-v2")
    }

    private fun testMessage() =
        DlqMessage(
            id = DlqMessageId(UUID.randomUUID()),
            messageKey = null,
            sourceTopic = "dlq.classification",
            partition = 0,
            offset = System.nanoTime(),
            payload = """{"synthetic":true}""",
            payloadEncoding = PayloadEncoding.TEXT,
            payloadInvalid = false,
            headers = emptyMap(),
            errorClass = "java.net.SocketTimeoutException",
            errorMessage = "request timed out",
            stackTrace = "java.net.SocketTimeoutException",
            receivedAt = Instant.now(),
            status = DlqMessageStatus.RECEIVED,
        )
}
