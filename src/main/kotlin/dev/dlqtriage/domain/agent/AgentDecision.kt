package dev.dlqtriage.domain.agent

import dev.dlqtriage.domain.classification.ExpectedAction
import dev.dlqtriage.domain.classification.FailureClass

data class AgentDecision(
    val classification: FailureClass,
    val confidence: Double,
    val evidence: List<String>,
    val suggestedAction: ExpectedAction,
    val reasoningSummary: String,
    val failureReason: String? = null,
) {
    init {
        require(confidence in 0.0..1.0)
        require(reasoningSummary.isNotBlank())
    }

    companion object {
        fun escalate(reason: String) =
            AgentDecision(
                classification = FailureClass.UNKNOWN,
                confidence = 0.0,
                evidence = emptyList(),
                suggestedAction = ExpectedAction.ESCALATE,
                reasoningSummary = reason,
                failureReason = reason,
            )
    }
}
