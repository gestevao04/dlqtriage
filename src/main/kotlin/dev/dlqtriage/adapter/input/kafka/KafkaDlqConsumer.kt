package dev.dlqtriage.adapter.input.kafka

import dev.dlqtriage.application.port.input.ClassifyDlqMessageUseCase
import dev.dlqtriage.application.port.input.IngestDlqMessageResult
import dev.dlqtriage.application.port.input.IngestDlqMessageUseCase
import dev.dlqtriage.domain.ingestion.DlqMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class KafkaDlqConsumer(
    private val recordMapper: KafkaDlqRecordMapper,
    private val ingestDlqMessageUseCase: IngestDlqMessageUseCase,
    private val classifyDlqMessageUseCase: ClassifyDlqMessageUseCase,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["\${dlq.kafka.topic:dlq}"],
        groupId = "\${dlq.kafka.group-id:dlq-triage}",
    )
    fun consume(
        record: ConsumerRecord<ByteArray, ByteArray>,
        acknowledgment: Acknowledgment,
    ) {
        try {
            val message = recordMapper.map(record)
            when (val result = ingestDlqMessageUseCase.ingest(message)) {
                is IngestDlqMessageResult.Received ->
                    classifyAndLog(message, record, "DLQ message persisted")
                is IngestDlqMessageResult.Duplicate ->
                    classifyAndLog(message, record, "Duplicate DLQ message ignored")
                is IngestDlqMessageResult.Failed -> {
                    logger.error(
                        "DLQ message persistence failed: topic={}, partition={}, offset={}, reason={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        result.reason,
                    )
                    return
                }
            }
            acknowledgment.acknowledge()
        } catch (exception: IllegalArgumentException) {
            logger.error(
                "DLQ message mapping failed: topic={}, partition={}, offset={}",
                record.topic(),
                record.partition(),
                record.offset(),
                exception,
            )
        } catch (exception: DataAccessException) {
            logger.error(
                "DLQ classification persistence failed: topic={}, partition={}, offset={}",
                record.topic(),
                record.partition(),
                record.offset(),
                exception,
            )
        } catch (exception: IllegalStateException) {
            logger.error(
                "DLQ message processing failed: topic={}, partition={}, offset={}",
                record.topic(),
                record.partition(),
                record.offset(),
                exception,
            )
        }
    }

    private fun classifyAndLog(
        message: DlqMessage,
        record: ConsumerRecord<ByteArray, ByteArray>,
        event: String,
    ) {
        val classification = classifyDlqMessageUseCase.classify(message)
        logger.info(
            "$event and classified: topic={}, partition={}, offset={}, messageId={}, class={}, confidence={}",
            record.topic(),
            record.partition(),
            record.offset(),
            message.id.value,
            classification.failureClass,
            classification.confidence,
        )
    }
}
