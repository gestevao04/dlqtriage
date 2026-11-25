package dev.dlqtriage.adapter.input.kafka

import dev.dlqtriage.application.port.input.ClassifyDlqMessageUseCase
import dev.dlqtriage.application.port.input.IngestDlqMessageResult
import dev.dlqtriage.application.port.input.IngestDlqMessageUseCase
import dev.dlqtriage.domain.classification.Classification
import dev.dlqtriage.domain.classification.FailureClass
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.kafka.support.Acknowledgment

class KafkaDlqConsumerTest {
    @Test
    fun persistsClassificationBeforeAcknowledgingNewMessage() {
        val record = testRecord()
        val acknowledgment = mockk<Acknowledgment>(relaxed = true)
        val ingestion = mockk<IngestDlqMessageUseCase>()
        val classifier = mockk<ClassifyDlqMessageUseCase>()
        every { ingestion.ingest(any()) } answers { IngestDlqMessageResult.Received(firstArg()) }
        every { classifier.classify(any()) } returns unknownClassification()

        consumer(ingestion, classifier).consume(record, acknowledgment)

        verify(exactly = 1) { classifier.classify(any()) }
        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun retriesClassificationOfDuplicateAndDoesNotAcknowledgeWhenPersistenceFails() {
        val record = testRecord()
        val acknowledgment = mockk<Acknowledgment>(relaxed = true)
        val ingestion = mockk<IngestDlqMessageUseCase>()
        val classifier = mockk<ClassifyDlqMessageUseCase>()
        every { ingestion.ingest(any()) } answers { IngestDlqMessageResult.Duplicate(firstArg()) }
        every { classifier.classify(any()) } throws DataAccessResourceFailureException("database unavailable")

        consumer(ingestion, classifier).consume(record, acknowledgment)

        verify(exactly = 1) { classifier.classify(any()) }
        verify(exactly = 0) { acknowledgment.acknowledge() }
    }

    private fun consumer(
        ingestion: IngestDlqMessageUseCase,
        classifier: ClassifyDlqMessageUseCase,
    ) = KafkaDlqConsumer(KafkaDlqRecordMapper(), ingestion, classifier)

    private fun testRecord() =
        ConsumerRecord(
            "dlq.test",
            0,
            1L,
            "synthetic".toByteArray(),
            """{"synthetic":true}""".toByteArray(),
        )

    private fun unknownClassification() =
        Classification(
            failureClass = FailureClass.UNKNOWN,
            confidence = 0.5,
            evidence = emptyList(),
            classifierVersion = "baseline-v1",
        )
}
