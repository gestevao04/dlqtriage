package dev.dlqtriage.sandbox

import dev.dlqtriage.adapter.input.kafka.KafkaDlqRecordMapper
import dev.dlqtriage.domain.classification.FailureClass
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.ByteArraySerializer
import java.util.Properties

fun main() {
    val topic = System.getenv("DLQ_TOPIC") ?: "dlq"
    val bootstrapServers = System.getenv("DLQ_BOOTSTRAP_SERVERS") ?: "localhost:9092"
    val properties =
        Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java)
        }

    KafkaProducer<ByteArray, ByteArray>(properties).use { producer ->
        syntheticFailures().forEachIndexed { index, failure ->
            val record =
                ProducerRecord(
                    topic,
                    null,
                    "synthetic-${failure.failureClass.name.lowercase()}-$index".toByteArray(),
                    failure.payload.toByteArray(),
                    listOf(
                        RecordHeader(
                            KafkaDlqRecordMapper.ERROR_CLASS_HEADER,
                            failure.errorClass.toByteArray(),
                        ),
                        RecordHeader(
                            KafkaDlqRecordMapper.ERROR_MESSAGE_HEADER,
                            failure.errorMessage.toByteArray(),
                        ),
                        RecordHeader(
                            KafkaDlqRecordMapper.STACK_TRACE_HEADER,
                            failure.stackTrace.toByteArray(),
                        ),
                    ),
                )
            producer.send(record).get()
            println("Published synthetic ${failure.failureClass} failure to $topic")
        }
    }
}

private data class SyntheticFailure(
    val failureClass: FailureClass,
    val errorClass: String,
    val errorMessage: String,
    val stackTrace: String,
    val payload: String,
)

private fun syntheticFailures() =
    listOf(
        SyntheticFailure(
            FailureClass.TRANSIENT_INFRASTRUCTURE,
            "java.net.SocketTimeoutException",
            "Payment service temporarily unavailable after timeout",
            "java.net.SocketTimeoutException: connection timed out",
            """{"synthetic":true,"case":"timeout"}""",
        ),
        SyntheticFailure(
            FailureClass.SCHEMA_MISMATCH,
            "com.example.SchemaMismatchException",
            "Schema mismatch: unknown field customerTier",
            "com.example.SchemaMismatchException: incompatible schema",
            """{"synthetic":true,"case":"schema"}""",
        ),
        SyntheticFailure(
            FailureClass.VALIDATION_ERROR,
            "jakarta.validation.ConstraintViolationException",
            "Validation failed: required field orderId must not be blank",
            "jakarta.validation.ConstraintViolationException",
            """{"synthetic":true,"case":"validation"}""",
        ),
        SyntheticFailure(
            FailureClass.PERMANENT_BUSINESS_ERROR,
            "com.example.InsufficientFundsException",
            "Insufficient funds for synthetic order",
            "com.example.InsufficientFundsException",
            """{"synthetic":true,"case":"business"}""",
        ),
        SyntheticFailure(
            FailureClass.UNKNOWN,
            "com.example.UnexpectedFailure",
            "Synthetic unclassified failure",
            "com.example.UnexpectedFailure",
            """{"synthetic":true,"case":"unknown"}""",
        ),
    )
