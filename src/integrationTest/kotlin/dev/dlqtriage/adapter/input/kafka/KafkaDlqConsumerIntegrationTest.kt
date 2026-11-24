package dev.dlqtriage.adapter.input.kafka

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Properties

@Testcontainers
class KafkaDlqConsumerIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        val kafka = KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"))
        private const val POLL_TIMEOUT_SECONDS = 10L
    }

    @Test
    fun publishesAndConsumesDlqRecord() {
        val producerProperties =
            Properties().apply {
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers)
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java)
            }
        val consumerProperties =
            Properties().apply {
                put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers)
                put(ConsumerConfig.GROUP_ID_CONFIG, "dlqtriage-integration")
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
                put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
                put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java)
                put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java)
            }

        KafkaProducer<ByteArray, ByteArray>(producerProperties).use { producer ->
            producer
                .send(
                    ProducerRecord("dlq.orders", "order-1".toByteArray(), """{"orderId":"123"}""".toByteArray()),
                ).get()
        }

        KafkaConsumer<ByteArray, ByteArray>(consumerProperties).use { consumer ->
            consumer.subscribe(listOf("dlq.orders"))
            val records = consumer.poll(Duration.ofSeconds(POLL_TIMEOUT_SECONDS))
            val record = records.iterator().next()
            consumer.commitSync()

            val mapped = KafkaDlqRecordMapper().map(record)
            assertThat(mapped.sourceTopic).isEqualTo("dlq.orders")
            assertThat(mapped.payload).isEqualTo("""{"orderId":"123"}""")
            assertThat(mapped.partition).isGreaterThanOrEqualTo(0)
        }
    }
}
