package dev.dlqtriage.application.port.output

import dev.dlqtriage.domain.ingestion.DlqMessage
import dev.dlqtriage.domain.ingestion.DlqMessageId

fun interface MessageLookupPort {
    fun findById(messageId: DlqMessageId): DlqMessage?
}
