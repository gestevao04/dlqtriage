package dev.dlqtriage.application.port.output

import dev.dlqtriage.domain.agent.AgentDecision

sealed interface AgentDecisionParseResult {
    data class Valid(
        val decision: AgentDecision,
    ) : AgentDecisionParseResult

    data class Invalid(
        val reason: String,
    ) : AgentDecisionParseResult
}

fun interface AgentDecisionParserPort {
    fun parse(output: String): AgentDecisionParseResult
}

data class InvestigatorPrompt(
    val version: String,
    val system: String,
    val user: String,
)

fun interface InvestigatorPromptPort {
    fun build(message: dev.dlqtriage.domain.ingestion.DlqMessage): InvestigatorPrompt
}
