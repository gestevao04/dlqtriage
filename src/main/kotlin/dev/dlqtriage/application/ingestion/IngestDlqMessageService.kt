package dev.dlqtriage.application.ingestion

import dev.dlqtriage.adapter.output.persistence.PersistenceException
import dev.dlqtriage.application.port.input.IngestDlqMessageResult
import dev.dlqtriage.application.port.input.IngestDlqMessageUseCase
import dev.dlqtriage.application.port.output.MessageRepository
import dev.dlqtriage.application.port.output.SaveMessageResult
import dev.dlqtriage.domain.ingestion.DlqMessage

class IngestDlqMessageService(
    private val messageRepository: MessageRepository,
) : IngestDlqMessageUseCase {
    override fun ingest(message: DlqMessage): IngestDlqMessageResult =
        try {
            when (val result = messageRepository.save(message)) {
                is SaveMessageResult.Saved -> IngestDlqMessageResult.Received(result.message)
                is SaveMessageResult.Duplicate -> IngestDlqMessageResult.Duplicate(result.message)
            }
        } catch (exception: PersistenceException) {
            IngestDlqMessageResult.Failed(exception.message ?: "Database persistence failed")
        }
}
