package dev.dlqtriage.adapter.agent.tool

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import dev.dlqtriage.application.port.output.AgentToolResult
import dev.dlqtriage.application.port.output.LlmToolDefinition
import dev.dlqtriage.application.port.output.ReadOnlyAgentTool
import org.springframework.stereotype.Component

@Component
class GetDependencyStatusTool(
    private val objectMapper: ObjectMapper,
) : ReadOnlyAgentTool {
    override val definition =
        LlmToolDefinition(
            name = "get_dependency_status",
            description = "Read the status of a known sandbox dependency fixture.",
            inputSchema =
                """
                {
                  "type": "object",
                  "properties": {"dependencyName": {"type": "string"}},
                  "required": ["dependencyName"],
                  "additionalProperties": false
                }
                """.trimIndent(),
        )

    override fun execute(argumentsJson: String): AgentToolResult =
        objectMapper
            .parseToolInput(argumentsJson)
            ?.let(::executeInput)
            ?: AgentToolResult.Failure("Arguments must be a JSON object.")

    private fun executeInput(input: ObjectNode): AgentToolResult {
        val name =
            input.requiredText("dependencyName")
                ?: return AgentToolResult.Failure("dependencyName is required.")
        return when (val result = dependencyStatus(name)) {
            DependencyStatus.Unknown -> AgentToolResult.Failure("Unknown sandbox dependency.")
            DependencyStatus.MissingFixture -> AgentToolResult.Failure("Dependency status fixture is missing.")
            is DependencyStatus.Found ->
                AgentToolResult.Success(
                    content = objectMapper.writeToolResult(result.status),
                    summary = "Read sandbox status for dependency $name.",
                )
        }
    }

    private fun dependencyStatus(name: String): DependencyStatus {
        val stream =
            javaClass.getResourceAsStream("/fixtures/dependency-status.json")
                ?: return DependencyStatus.MissingFixture
        val status = stream.use { objectMapper.readTree(it).get(name) }
        return status?.let(DependencyStatus::Found) ?: DependencyStatus.Unknown
    }

    private sealed interface DependencyStatus {
        data class Found(
            val status: JsonNode,
        ) : DependencyStatus

        data object Unknown : DependencyStatus

        data object MissingFixture : DependencyStatus
    }
}
