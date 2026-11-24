package dev.dlqtriage.domain.ingestion

import java.time.Instant
import java.util.UUID

data class DlqMessageId(
    val value: UUID,
)

data class DlqMessage(
    val id: DlqMessageId,
    val messageKey: String?,
    val sourceTopic: String,
    val partition: Int,
    val offset: Long,
    val payload: String,
    val payloadEncoding: PayloadEncoding,
    val payloadInvalid: Boolean,
    val headers: Map<String, String>,
    val errorClass: String,
    val errorMessage: String?,
    val stackTrace: String?,
    val receivedAt: Instant,
    val status: DlqMessageStatus,
)
