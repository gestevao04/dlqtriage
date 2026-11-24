package dev.dlqtriage.application.port.input

import dev.dlqtriage.domain.ingestion.DlqMessage

sealed interface IngestDlqMessageResult {
    data class Received(
        val message: DlqMessage,
    ) : IngestDlqMessageResult

    data class Duplicate(
        val message: DlqMessage,
    ) : IngestDlqMessageResult

    data class Failed(
        val reason: String,
    ) : IngestDlqMessageResult
}

fun interface IngestDlqMessageUseCase {
    fun ingest(message: DlqMessage): IngestDlqMessageResult
}
