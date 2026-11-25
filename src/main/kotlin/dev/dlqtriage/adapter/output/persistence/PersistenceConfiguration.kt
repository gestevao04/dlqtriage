package dev.dlqtriage.adapter.output.persistence

import dev.dlqtriage.adapter.input.kafka.KafkaDlqRecordMapper
import dev.dlqtriage.application.classification.ClassifyDlqMessageService
import dev.dlqtriage.application.classification.RuleBasedClassifier
import dev.dlqtriage.application.ingestion.IngestDlqMessageService
import dev.dlqtriage.application.port.output.ClassificationRepository
import dev.dlqtriage.application.port.output.MessageRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PersistenceConfiguration {
    @Bean
    fun ingestDlqMessageService(messageRepository: MessageRepository) = IngestDlqMessageService(messageRepository)

    @Bean
    fun ruleBasedClassifier() = RuleBasedClassifier()

    @Bean
    fun classifyDlqMessageService(
        classifier: RuleBasedClassifier,
        classificationRepository: ClassificationRepository,
    ) = ClassifyDlqMessageService(classifier, classificationRepository)

    @Bean
    fun kafkaDlqRecordMapper() = KafkaDlqRecordMapper()
}
