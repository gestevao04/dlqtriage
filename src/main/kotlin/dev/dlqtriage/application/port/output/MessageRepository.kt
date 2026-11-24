package dev.dlqtriage.application.port.output

import dev.dlqtriage.domain.ingestion.DlqMessage

sealed interface SaveMessageResult {
    data class Saved(
        val message: DlqMessage,
    ) : SaveMessageResult

    data class Duplicate(
        val message: DlqMessage,
    ) : SaveMessageResult
}

fun interface MessageRepository {
    fun save(message: DlqMessage): SaveMessageResult
}
