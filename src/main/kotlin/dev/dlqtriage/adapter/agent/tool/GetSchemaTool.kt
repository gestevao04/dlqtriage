package dev.dlqtriage.adapter.agent.tool

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import dev.dlqtriage.application.port.output.AgentToolResult
import dev.dlqtriage.application.port.output.LlmToolDefinition
import dev.dlqtriage.application.port.output.ReadOnlyAgentTool
import org.springframework.stereotype.Component

@Component
class GetSchemaTool(
    private val objectMapper: ObjectMapper,
) : ReadOnlyAgentTool {
    override val definition =
        LlmToolDefinition(
            name = "get_schema",
            description = "Read a known schema from the sandbox fixtures.",
            inputSchema =
                """
                {
                  "type": "object",
                  "properties": {"schemaName": {"type": "string"}},
                  "required": ["schemaName"],
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
            input.requiredText("schemaName")
                ?: return AgentToolResult.Failure("schemaName is required.")
        return when (val result = loadSchema(name)) {
            SchemaLoad.Unknown -> AgentToolResult.Failure("Unknown sandbox schema.")
            SchemaLoad.MissingFixture -> AgentToolResult.Failure("Sandbox schema fixture is missing.")
            is SchemaLoad.Found ->
                AgentToolResult.Success(
                    content = result.content,
                    summary = "Loaded sandbox schema $name.",
                )
        }
    }

    private fun loadSchema(name: String): SchemaLoad =
        SCHEMAS[name]
            ?.let { resourcePath ->
                javaClass
                    .getResourceAsStream(resourcePath)
                    ?.bufferedReader()
                    ?.use { SchemaLoad.Found(it.readText()) }
                    ?: SchemaLoad.MissingFixture
            }
            ?: SchemaLoad.Unknown

    companion object {
        private val SCHEMAS = mapOf("order-v1" to "/fixtures/schemas/order-v1.json")
    }

    private sealed interface SchemaLoad {
        data class Found(
            val content: String,
        ) : SchemaLoad

        data object Unknown : SchemaLoad

        data object MissingFixture : SchemaLoad
    }
}
