package dev.dlqtriage.domain.agent

import dev.dlqtriage.domain.ingestion.DlqMessageId
import java.time.Instant
import java.util.UUID

enum class AgentRunStatus {
    COMPLETED,
    ESCALATED,
}

data class AgentRun(
    val id: UUID,
    val messageId: DlqMessageId,
    val promptVersion: String,
    val decision: AgentDecision,
    val status: AgentRunStatus,
    val startedAt: Instant,
    val completedAt: Instant,
    val inputTokens: Int,
    val outputTokens: Int,
) {
    init {
        require(promptVersion.isNotBlank())
        require(!completedAt.isBefore(startedAt))
        require(inputTokens >= 0)
        require(outputTokens >= 0)
    }
}

data class AgentStep(
    val runId: UUID,
    val sequence: Int,
    val summarizedInput: String,
    val toolCalled: String?,
    val summarizedResult: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val durationMillis: Long,
) {
    init {
        require(sequence > 0)
        require(summarizedInput.isNotBlank())
        require(summarizedResult.isNotBlank())
        require(inputTokens >= 0)
        require(outputTokens >= 0)
        require(durationMillis >= 0)
    }
}

data class AgentInvestigation(
    val run: AgentRun,
    val steps: List<AgentStep>,
)
