package dev.dlqtriage.domain.classification

enum class FailureClass(
    val expectedAction: ExpectedAction,
) {
    TRANSIENT_INFRASTRUCTURE(ExpectedAction.REPROCESS),
    SCHEMA_MISMATCH(ExpectedAction.ESCALATE),
    VALIDATION_ERROR(ExpectedAction.ESCALATE),
    PERMANENT_BUSINESS_ERROR(ExpectedAction.ESCALATE),
    UNKNOWN(ExpectedAction.ESCALATE),
}

enum class ExpectedAction {
    REPROCESS,
    ESCALATE,
}

data class Classification(
    val failureClass: FailureClass,
    val confidence: Double,
    val evidence: List<String>,
    val classifierVersion: String,
) {
    init {
        require(confidence in 0.0..1.0)
        require(classifierVersion.isNotBlank())
    }
}
