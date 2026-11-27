package dev.dlqtriage.adapter.agent.tool

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.dlqtriage.application.port.output.AgentToolResult
import dev.dlqtriage.application.port.output.MessageLookupPort
import dev.dlqtriage.application.port.output.ReadOnlyAgentTool
import dev.dlqtriage.application.port.output.SimilarFailure
import dev.dlqtriage.application.port.output.SimilarFailureSearchPort
import dev.dlqtriage.domain.ingestion.DlqMessage
import dev.dlqtriage.domain.ingestion.DlqMessageId
import dev.dlqtriage.domain.ingestion.DlqMessageStatus
import dev.dlqtriage.domain.ingestion.PayloadEncoding
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ReadOnlyAgentToolsTest {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun exposesTheFourRequiredToolsUsingOnlyReadPortsAndFixtures() {
        val message = testMessage()
        var searched = false
        val tools =
            listOf(
                GetMessageTool(MessageLookupPort { message }, objectMapper),
                GetSchemaTool(objectMapper),
                FindSimilarFailuresTool(
                    SimilarFailureSearchPort {
                        searched = true
                        listOf(
                            SimilarFailure(
                                messageId = message.id,
                                errorClass = message.errorClass,
                                errorMessage = message.errorMessage,
                                classification = "UNKNOWN",
                                confidence = 0.5,
                            ),
                        )
                    },
                    objectMapper,
                ),
                GetDependencyStatusTool(objectMapper),
            )

        assertToolDefinitions(tools)
        assertReadOnlyToolTypes()
        assertToolResults(tools, message)
        assertThat(searched).isTrue()
    }

    private fun assertToolDefinitions(tools: List<ReadOnlyAgentTool>) {
        assertThat(tools.map { it.definition.name })
            .containsExactlyInAnyOrder("get_message", "get_schema", "find_similar_failures", "get_dependency_status")
    }

    private fun assertReadOnlyToolTypes() {
        val toolClasses =
            listOf(
                GetMessageTool::class.java,
                GetSchemaTool::class.java,
                FindSimilarFailuresTool::class.java,
                GetDependencyStatusTool::class.java,
            )
        assertThat(
            toolClasses.all {
                ReadOnlyAgentTool::class.java.isAssignableFrom(it)
            },
        ).isTrue()
    }

    private fun assertToolResults(
        tools: List<ReadOnlyAgentTool>,
        message: DlqMessage,
    ) {
        val messageResult = tools[0].execute("""{"messageId":"${message.id.value}"}""")
        assertThat(messageResult).isInstanceOf(AgentToolResult.Success::class.java)
        assertThat(
            objectMapper.readTree((messageResult as AgentToolResult.Success).content).path("payload").asText(),
        ).isEqualTo(message.payload)

        val schemaResult = tools[1].execute("""{"schemaName":"order-v1"}""")
        assertThat(schemaResult.toString()).contains("orderId")

        val similarResult =
            tools[2].execute(
                """
                {
                  "messageId": "${message.id.value}",
                  "errorClass": "${message.errorClass}",
                  "errorMessage": "socket timeout"
                }
                """.trimIndent(),
            )
        assertThat(similarResult.toString()).contains("UNKNOWN")

        val dependencyResult = tools[3].execute("""{"dependencyName":"inventory"}""")
        assertThat(dependencyResult.toString()).contains("DEGRADED")
    }

    @Test
    fun rejectsMalformedAndUnknownToolInputs() {
        assertThat(
            GetSchemaTool(objectMapper).execute("not json").toString(),
        ).contains("Arguments must be a JSON object")
        assertThat(
            GetSchemaTool(objectMapper).execute("""{"schemaName":"../../application.yaml"}""").toString(),
        ).contains("Unknown sandbox schema")
        assertThat(
            GetDependencyStatusTool(objectMapper).execute("""{"dependencyName":"unknown"}""").toString(),
        ).contains("Unknown sandbox dependency")
    }

    private fun testMessage() =
        DlqMessage(
            id = DlqMessageId(UUID.randomUUID()),
            messageKey = "order-1",
            sourceTopic = "dlq.orders",
            partition = 0,
            offset = 3,
            payload = """{"orderId":"123"}""",
            payloadEncoding = PayloadEncoding.TEXT,
            payloadInvalid = false,
            headers = mapOf("x-origin" to "orders"),
            errorClass = "java.net.SocketTimeoutException",
            errorMessage = "socket timeout",
            stackTrace = "java.net.SocketTimeoutException",
            receivedAt = Instant.EPOCH,
            status = DlqMessageStatus.RECEIVED,
        )
}
