package dev.dlqtriage.application.port.input

import dev.dlqtriage.domain.classification.Classification
import dev.dlqtriage.domain.ingestion.DlqMessage

fun interface Classifier {
    fun classify(message: DlqMessage): Classification
}
