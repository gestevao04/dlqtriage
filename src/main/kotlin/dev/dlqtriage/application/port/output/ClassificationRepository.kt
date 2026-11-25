package dev.dlqtriage.application.port.output

import dev.dlqtriage.domain.classification.Classification
import dev.dlqtriage.domain.ingestion.DlqMessageId

fun interface ClassificationRepository {
    fun save(
        messageId: DlqMessageId,
        classification: Classification,
    )
}
