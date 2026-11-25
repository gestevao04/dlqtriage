package dev.dlqtriage.application.classification

import dev.dlqtriage.application.port.input.Classifier
import dev.dlqtriage.application.port.output.ClassificationRepository
import dev.dlqtriage.domain.classification.Classification
import dev.dlqtriage.domain.classification.FailureClass
import dev.dlqtriage.domain.ingestion.DlqMessage
import dev.dlqtriage.domain.ingestion.DlqMessageId
import dev.dlqtriage.domain.ingestion.DlqMessageStatus
import dev.dlqtriage.domain.ingestion.PayloadEncoding
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ClassifyDlqMessageServiceTest {
    @Test
    fun classifiesAndPersistsTheResult() {
        val classifier = mockk<Classifier>()
        val repository = mockk<ClassificationRepository>(relaxed = true)
        val message =
            DlqMessage(
                id = DlqMessageId(UUID.randomUUID()),
                messageKey = null,
                sourceTopic = "dlq.test",
                partition = 0,
                offset = 1,
                payload = "",
                payloadEncoding = PayloadEncoding.TEXT,
                payloadInvalid = false,
                headers = emptyMap(),
                errorClass = "CustomFailure",
                errorMessage = null,
                stackTrace = null,
                receivedAt = Instant.EPOCH,
                status = DlqMessageStatus.RECEIVED,
            )
        val expected =
            Classification(
                failureClass = FailureClass.UNKNOWN,
                confidence = 0.5,
                evidence = emptyList(),
                classifierVersion = RuleBasedClassifier.VERSION,
            )
        every { classifier.classify(message) } returns expected

        val result = ClassifyDlqMessageService(classifier, repository).classify(message)

        assertThat(result).isEqualTo(expected)
        verify(exactly = 1) { repository.save(message.id, expected) }
    }
}
