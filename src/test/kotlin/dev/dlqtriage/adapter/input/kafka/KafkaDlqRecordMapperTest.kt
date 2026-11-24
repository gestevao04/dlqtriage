package dev.dlqtriage.adapter.input.kafka

import dev.dlqtriage.domain.ingestion.PayloadEncoding
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class KafkaDlqRecordMapperTest {
    private val mapper = KafkaDlqRecordMapper()

    @Test
    fun `maps headers and missing error header defaults to unknown`() {
        val record =
            ConsumerRecord<ByteArray, ByteArray>(
                "dlq.orders",
                2,
                42,
                "order-1".toByteArray(),
                """{"orderId":"123"}""".toByteArray(),
            )
        record.headers().add(
            RecordHeader(
                KafkaDlqRecordMapper.STACK_TRACE_HEADER,
                "at dev.DlqTest.run".toByteArray(StandardCharsets.UTF_8),
            ),
        )

        val message = mapper.map(record)

        assertThat(message.sourceTopic).isEqualTo("dlq.orders")
        assertThat(message.partition).isEqualTo(2)
        assertThat(message.offset).isEqualTo(42)
        assertThat(message.errorClass).isEqualTo("UNKNOWN")
        assertThat(message.stackTrace).isEqualTo("at dev.DlqTest.run")
        assertThat(message.payloadEncoding).isEqualTo(PayloadEncoding.TEXT)
    }

    @Test
    fun `encodes invalid payload as base64`() {
        val record =
            ConsumerRecord<ByteArray, ByteArray>(
                "dlq.orders",
                0,
                7,
                null,
                byteArrayOf(0xc3.toByte(), 0x28),
            )

        val message = mapper.map(record)

        assertThat(message.payloadEncoding).isEqualTo(PayloadEncoding.BASE64)
        assertThat(message.payloadInvalid).isTrue()
        assertThat(message.payload).isEqualTo("wyg=")
    }
}
