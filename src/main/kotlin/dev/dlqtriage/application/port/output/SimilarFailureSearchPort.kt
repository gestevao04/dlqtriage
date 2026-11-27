package dev.dlqtriage.application.port.output

import dev.dlqtriage.domain.ingestion.DlqMessageId

data class SimilarFailureQuery(
    val messageId: DlqMessageId,
    val errorClass: String,
    val errorMessage: String?,
)

data class SimilarFailure(
    val messageId: DlqMessageId,
    val errorClass: String,
    val errorMessage: String?,
    val classification: String?,
    val confidence: Double?,
)

fun interface SimilarFailureSearchPort {
    fun findSimilar(query: SimilarFailureQuery): List<SimilarFailure>
}
