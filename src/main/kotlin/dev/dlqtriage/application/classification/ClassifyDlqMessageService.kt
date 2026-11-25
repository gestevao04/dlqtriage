package dev.dlqtriage.application.classification

import dev.dlqtriage.application.port.input.Classifier
import dev.dlqtriage.application.port.input.ClassifyDlqMessageUseCase
import dev.dlqtriage.application.port.output.ClassificationRepository
import dev.dlqtriage.domain.classification.Classification
import dev.dlqtriage.domain.ingestion.DlqMessage

class ClassifyDlqMessageService(
    private val classifier: Classifier,
    private val classificationRepository: ClassificationRepository,
) : ClassifyDlqMessageUseCase {
    override fun classify(message: DlqMessage): Classification {
        val classification = classifier.classify(message)
        classificationRepository.save(message.id, classification)
        return classification
    }
}
