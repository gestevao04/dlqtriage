package dev.dlqtriage.application.port.output

import dev.dlqtriage.domain.agent.AgentInvestigation

fun interface AgentRunRepository {
    fun save(investigation: AgentInvestigation)
}
