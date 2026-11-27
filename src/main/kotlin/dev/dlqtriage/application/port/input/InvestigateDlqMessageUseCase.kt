package dev.dlqtriage.application.port.input

import dev.dlqtriage.domain.agent.AgentInvestigation
import dev.dlqtriage.domain.ingestion.DlqMessage

fun interface InvestigateDlqMessageUseCase {
    fun investigate(message: DlqMessage): AgentInvestigation
}
