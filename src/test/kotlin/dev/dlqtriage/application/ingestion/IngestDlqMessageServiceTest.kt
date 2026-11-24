package dev.dlqtriage.application.ingestion

import dev.dlqtriage.adapter.output.persistence.PersistenceException
import dev.dlqtriage.application.port.input.IngestDlqMessageResult
import dev.dlqtriage.application.port.output.MessageRepository
import dev.dlqtriage.application.port.output.SaveMessageResult
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

class IngestDlqMessageServiceTest {
    private val message =
        DlqMessage(
            id = DlqMessageId(UUID.randomUUID()),
            messageKey = "order-1",
            sourceTopic = "dlq.orders",
            partition = 1,
            offset = 10,
            payload = "{}",
            payloadEncoding = PayloadEncoding.TEXT,
            payloadInvalid = false,
            headers = emptyMap(),
            errorClass = "UNKNOWN",
            errorMessage = null,
            stackTrace = null,
            receivedAt = Instant.parse("2026-01-01T00:00:00Z"),
            status = DlqMessageStatus.RECEIVED,
        )

    @Test
    fun `returns received after repository saves message`() {
        val repository = mockk<MessageRepository>()
        every { repository.save(message) } returns SaveMessageResult.Saved(message)
        val service = IngestDlqMessageService(repository)

        val result = service.ingest(message)

        assertThat(result).isInstanceOf(IngestDlqMessageResult.Received::class.java)
        verify(exactly = 1) { repository.save(message) }
    }

    @Test
    fun `returns duplicate when repository finds existing key`() {
        val repository = mockk<MessageRepository>()
        every { repository.save(message) } returns SaveMessageResult.Duplicate(message)
        val service = IngestDlqMessageService(repository)

        val result = service.ingest(message)

        assertThat(result).isInstanceOf(IngestDlqMessageResult.Duplicate::class.java)
    }

    @Test
    fun `returns failed result when database throws`() {
        val repository = mockk<MessageRepository>()
        every { repository.save(message) } throws PersistenceException("database unavailable", null)
        val service = IngestDlqMessageService(repository)

        val result = service.ingest(message)

        assertThat(result).isInstanceOf(IngestDlqMessageResult.Failed::class.java)
    }
}
