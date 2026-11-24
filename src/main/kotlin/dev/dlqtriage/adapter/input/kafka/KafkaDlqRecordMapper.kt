package dev.dlqtriage.adapter.input.kafka

import dev.dlqtriage.domain.ingestion.DlqMessage
import dev.dlqtriage.domain.ingestion.DlqMessageId
import dev.dlqtriage.domain.ingestion.DlqMessageStatus
import dev.dlqtriage.domain.ingestion.PayloadEncoding
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID

class KafkaDlqRecordMapper {
    fun map(record: ConsumerRecord<ByteArray, ByteArray>): DlqMessage {
        val headers =
            record.headers().associate { header ->
                header.key() to String(header.value() ?: ByteArray(0), StandardCharsets.UTF_8)
            }
        val payload = decodePayload(record.value())
        val receivedAt = Instant.now()

        return DlqMessage(
            id = DlqMessageId(UUID.randomUUID()),
            messageKey = record.key()?.let { String(it, StandardCharsets.UTF_8) },
            sourceTopic = record.topic(),
            partition = record.partition(),
            offset = record.offset(),
            payload = payload.first,
            payloadEncoding = payload.second,
            payloadInvalid = payload.second == PayloadEncoding.BASE64,
            headers = headers,
            errorClass = headers[ERROR_CLASS_HEADER] ?: "UNKNOWN",
            errorMessage = headers[ERROR_MESSAGE_HEADER],
            stackTrace = headers[STACK_TRACE_HEADER],
            receivedAt = receivedAt,
            status = DlqMessageStatus.RECEIVED,
        )
    }

    private fun decodePayload(value: ByteArray?): Pair<String, PayloadEncoding> {
        if (value == null) {
            return "" to PayloadEncoding.TEXT
        }
        return try {
            val decoded =
                StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(value))
                    .toString()
            decoded to PayloadEncoding.TEXT
        } catch (_: CharacterCodingException) {
            Base64.getEncoder().encodeToString(value) to PayloadEncoding.BASE64
        }
    }

    companion object {
        const val ERROR_CLASS_HEADER = "x-exception-fqcn"
        const val ERROR_MESSAGE_HEADER = "x-exception-message"
        const val STACK_TRACE_HEADER = "x-exception-stacktrace"
    }
}
