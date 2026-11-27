package dev.dlqtriage.adapter.output.persistence

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.dlqtriage.adapter.agent.InvestigatorPromptBuilder
import dev.dlqtriage.adapter.output.llm.JacksonAgentDecisionParser
import dev.dlqtriage.adapter.output.llm.ProgrammableFakeLlmAdapter
import dev.dlqtriage.application.agent.InvestigateDlqMessageService
import dev.dlqtriage.application.port.output.LlmCompletionResult
import dev.dlqtriage.application.port.output.SimilarFailureQuery
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
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Testcontainers
class PostgresAgentRunRepositoryIntegrationTest {
    companion object {
        private const val EXPECTED_INPUT_TOKENS = 31
        private const val EXPECTED_OUTPUT_TOKENS = 12

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
    fun persistsRunAndStepWithPromptVersionAndTokenUsage() {
        val mapper = jacksonObjectMapper()
        val message = testMessage()
        PostgresMessageRepository(jdbcTemplate).save(message)
        val fakeLlm = ProgrammableFakeLlmAdapter()
        fakeLlm.enqueue(
            LlmCompletionResult.Success(
                content =
                    """
                    {
                      "classification": "TRANSIENT_INFRASTRUCTURE",
                      "confidence": 0.9,
                      "evidence": ["SocketTimeoutException"],
                      "suggested_action": "REPROCESS",
                      "reasoning_summary": "The connection timed out."
                    }
                    """.trimIndent(),
                toolCalls = emptyList(),
                inputTokens = EXPECTED_INPUT_TOKENS,
                outputTokens = EXPECTED_OUTPUT_TOKENS,
            ),
        )
        val service =
            InvestigateDlqMessageService(
                llmPort = fakeLlm,
                tools = emptyList(),
                promptBuilder = InvestigatorPromptBuilder(mapper),
                decisionParser = JacksonAgentDecisionParser(mapper),
                agentRunRepository = PostgresAgentRunRepository(jdbcTemplate, mapper),
                clock = Clock.systemUTC(),
            )

        val investigation = service.investigate(message)
        val persistedRun =
            jdbcTemplate.queryForMap(
                """
                SELECT prompt_version, status, classification, suggested_action, input_tokens, output_tokens
                FROM agent_run WHERE id = ?
                """.trimIndent(),
                investigation.run.id,
            )
        val persistedStep =
            jdbcTemplate.queryForMap(
                """
                SELECT summarized_input, tool_called, input_tokens, output_tokens
                FROM agent_step WHERE run_id = ? AND sequence = 1
                """.trimIndent(),
                investigation.run.id,
            )

        assertThat(persistedRun["prompt_version"]).isEqualTo("investigator-v1")
        assertThat(persistedRun["status"]).isEqualTo("COMPLETED")
        assertThat(persistedRun["classification"]).isEqualTo("TRANSIENT_INFRASTRUCTURE")
        assertThat(persistedRun["suggested_action"]).isEqualTo("REPROCESS")
        assertThat(persistedRun["input_tokens"]).isEqualTo(EXPECTED_INPUT_TOKENS)
        assertThat(persistedRun["output_tokens"]).isEqualTo(EXPECTED_OUTPUT_TOKENS)
        assertThat(persistedStep["summarized_input"]).isEqualTo("Investigator request and message context.")
        assertThat(persistedStep["tool_called"]).isNull()
        assertThat(persistedStep["input_tokens"]).isEqualTo(EXPECTED_INPUT_TOKENS)
        assertThat(persistedStep["output_tokens"]).isEqualTo(EXPECTED_OUTPUT_TOKENS)
    }

    @Test
    fun readOnlyAdaptersFindMessagesAndSimilarFailures() {
        val mapper = jacksonObjectMapper()
        val original =
            testMessage().copy(
                errorClass = "ReadOnlyAdapterTestException",
                errorMessage = "read-only adapter network timeout",
            )
        val similar =
            original.copy(
                id = DlqMessageId(UUID.randomUUID()),
                offset = original.offset + 1,
                errorMessage = "read-only adapter timed out while connecting",
            )
        val messageRepository = PostgresMessageRepository(jdbcTemplate)
        messageRepository.save(original)
        messageRepository.save(similar)

        val lookedUp = PostgresMessageLookupAdapter(jdbcTemplate, mapper).findById(original.id)
        val matches =
            PostgresSimilarFailureSearchAdapter(jdbcTemplate)
                .findSimilar(
                    SimilarFailureQuery(
                        messageId = original.id,
                        errorClass = original.errorClass,
                        errorMessage = "read-only adapter timed out",
                    ),
                )
        val classOnlyMatches =
            PostgresSimilarFailureSearchAdapter(jdbcTemplate)
                .findSimilar(
                    SimilarFailureQuery(
                        messageId = original.id,
                        errorClass = original.errorClass,
                        errorMessage = null,
                    ),
                )

        assertThat(lookedUp).isEqualTo(original)
        assertThat(matches).hasSize(1)
        assertThat(matches.single().messageId).isEqualTo(similar.id)
        assertThat(matches.single().errorMessage).contains("timed out")
        assertThat(classOnlyMatches).hasSize(1)
    }

    private fun testMessage() =
        DlqMessage(
            id = DlqMessageId(UUID.randomUUID()),
            messageKey = "order-1",
            sourceTopic = "dlq.investigator",
            partition = 0,
            offset = System.nanoTime(),
            payload = """{"orderId":"synthetic"}""",
            payloadEncoding = PayloadEncoding.TEXT,
            payloadInvalid = false,
            headers = mapOf("x-fixture" to "true"),
            errorClass = "java.net.SocketTimeoutException",
            errorMessage = "request timed out",
            stackTrace = "java.net.SocketTimeoutException",
            receivedAt = Instant.EPOCH,
            status = DlqMessageStatus.RECEIVED,
        )
}
