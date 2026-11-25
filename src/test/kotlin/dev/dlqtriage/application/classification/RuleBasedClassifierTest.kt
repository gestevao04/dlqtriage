package dev.dlqtriage.application.classification

import dev.dlqtriage.domain.classification.FailureClass
import dev.dlqtriage.domain.ingestion.DlqMessage
import dev.dlqtriage.domain.ingestion.DlqMessageId
import dev.dlqtriage.domain.ingestion.DlqMessageStatus
import dev.dlqtriage.domain.ingestion.PayloadEncoding
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class RuleBasedClassifierTest {
    private val classifier = RuleBasedClassifier()

    @Test
    fun classifiesEachFailurePatternWithEvidence() {
        val cases =
            listOf(
                "java.net.SocketTimeoutException" to FailureClass.TRANSIENT_INFRASTRUCTURE,
                "com.example.SchemaMismatchException" to FailureClass.SCHEMA_MISMATCH,
                "jakarta.validation.ConstraintViolationException" to FailureClass.VALIDATION_ERROR,
                "com.example.InsufficientFundsException" to FailureClass.PERMANENT_BUSINESS_ERROR,
            )

        cases.forEach { (errorClass, expected) ->
            val result = classifier.classify(message(errorClass = errorClass))

            assertThat(result.failureClass).isEqualTo(expected)
            assertThat(result.evidence).anyMatch { it.startsWith("errorClass:") }
            assertThat(result.confidence).isEqualTo(0.99)
            assertThat(result.classifierVersion).isEqualTo(RuleBasedClassifier.VERSION)
        }
    }

    @Test
    fun classifiesMessageAndStackTracePatternsWhenErrorClassDoesNotMatch() {
        val messageMatch =
            classifier.classify(
                message(
                    errorClass = "CustomFailure",
                    errorMessage = "The service is temporarily unavailable",
                ),
            )
        val stackTraceMatch =
            classifier.classify(
                message(
                    errorClass = "CustomFailure",
                    errorMessage = null,
                    stackTrace = "Caused by: java.net.SocketTimeoutException",
                ),
            )

        assertThat(messageMatch.failureClass).isEqualTo(FailureClass.TRANSIENT_INFRASTRUCTURE)
        assertThat(messageMatch.evidence).contains("errorMessage: temporarily unavailable")
        assertThat(stackTraceMatch.failureClass).isEqualTo(FailureClass.TRANSIENT_INFRASTRUCTURE)
        assertThat(stackTraceMatch.evidence).contains("stackTrace: SocketTimeoutException")
    }

    @Test
    fun prioritizesErrorClassOverConflictingMessageAndLeavesUnmatchedFailureUnknown() {
        val conflict =
            classifier.classify(
                message(
                    errorClass = "java.net.SocketTimeoutException",
                    errorMessage = "Validation failed: required field orderId",
                ),
            )
        val unmatched = classifier.classify(message(errorClass = "CustomFailure", errorMessage = "Unknown issue"))

        assertThat(conflict.failureClass).isEqualTo(FailureClass.TRANSIENT_INFRASTRUCTURE)
        assertThat(conflict.evidence).allMatch { it.startsWith("errorClass:") }
        assertThat(unmatched.failureClass).isEqualTo(FailureClass.UNKNOWN)
        assertThat(unmatched.evidence).isEmpty()
        assertThat(unmatched.confidence).isEqualTo(0.5)
    }

    private fun message(
        errorClass: String,
        errorMessage: String? = null,
        stackTrace: String? = null,
    ) = DlqMessage(
        id = DlqMessageId(UUID.randomUUID()),
        messageKey = null,
        sourceTopic = "dlq.test",
        partition = 0,
        offset = 0,
        payload = "",
        payloadEncoding = PayloadEncoding.TEXT,
        payloadInvalid = false,
        headers = emptyMap(),
        errorClass = errorClass,
        errorMessage = errorMessage,
        stackTrace = stackTrace,
        receivedAt = Instant.EPOCH,
        status = DlqMessageStatus.RECEIVED,
    )
}
