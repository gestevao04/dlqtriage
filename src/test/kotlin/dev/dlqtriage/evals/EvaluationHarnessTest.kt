package dev.dlqtriage.evals

import dev.dlqtriage.application.classification.RuleBasedClassifier
import dev.dlqtriage.domain.classification.ExpectedAction
import dev.dlqtriage.domain.classification.FailureClass
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class EvaluationHarnessTest {
    @Test
    fun reportsOverallPerClassAndConfusionMatrixMetrics() {
        val cases =
            listOf(
                case("a", FailureClass.TRANSIENT_INFRASTRUCTURE, "java.net.SocketTimeoutException"),
                case("b", FailureClass.VALIDATION_ERROR, "ConstraintViolationException"),
                case("c", FailureClass.UNKNOWN, "CustomFailure"),
            )

        val report = buildReport(cases, RuleBasedClassifier())

        assertThat(report.overall.cases).isEqualTo(3)
        assertThat(report.overall.accuracy).isEqualTo(1.0)
        assertThat(report.overall.perClassAccuracy["TRANSIENT_INFRASTRUCTURE"]).isEqualTo(1.0)
        assertThat(report.overall.confusionMatrix["VALIDATION_ERROR"]?.get("VALIDATION_ERROR")).isEqualTo(1)
        assertThat(report.perSplit["train"]?.cases).isEqualTo(2)
        assertThat(report.perSplit["held-out"]?.cases).isEqualTo(1)
        assertThat(report.datasetRequirementsMet).isFalse()
    }

    @Test
    fun rejectsIncorrectActionsAndDuplicateCaseIds() {
        val incorrectAction =
            case("a", FailureClass.TRANSIENT_INFRASTRUCTURE, "TimeoutException")
                .copy(correctAction = ExpectedAction.ESCALATE)
        val duplicateId =
            case("a", FailureClass.UNKNOWN, "CustomFailure")

        assertThatThrownBy { buildReport(listOf(incorrectAction), RuleBasedClassifier()) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            buildReport(
                listOf(case("a", FailureClass.UNKNOWN, "CustomFailure"), duplicateId),
                RuleBasedClassifier(),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun recognizesCompleteDatasetProvenanceReviewAndHeldOutCoverage() {
        val cases =
            (0 until 100).map { index ->
                val generator =
                    when (index % 5) {
                        0 -> "human"
                        1, 2 -> "llama3.1:8b"
                        else -> "gemma3:4b"
                    }
                case(
                    id = "case-$index",
                    failureClass = FailureClass.entries[index % FailureClass.entries.size],
                    errorClass = "CustomFailure",
                    provenance = CaseProvenance(generator, index < 20),
                    split = if (index % 5 == 0) "held-out" else "train",
                )
            }

        assertThat(buildReport(cases, RuleBasedClassifier()).datasetRequirementsMet).isTrue()
    }

    @Test
    fun includesMisclassificationDetailsForManualReview() {
        val report =
            buildReport(
                listOf(case("incorrect-schema", FailureClass.SCHEMA_MISMATCH, "CustomFailure")),
                RuleBasedClassifier(),
            )

        assertThat(report.misclassifications).hasSize(1)
        assertThat(report.misclassifications.single().caseId).isEqualTo("incorrect-schema")
        assertThat(report.misclassifications.single().expectedClass).isEqualTo(FailureClass.SCHEMA_MISMATCH)
        assertThat(report.misclassifications.single().predictedClass).isEqualTo(FailureClass.UNKNOWN)
    }

    private fun case(
        id: String,
        failureClass: FailureClass,
        errorClass: String,
        provenance: CaseProvenance = CaseProvenance("test", false),
        split: String = if (id == "c") "held-out" else "train",
    ) = EvaluationCase(
        id = id,
        errorClass = errorClass,
        errorMessage = null,
        stackTrace = null,
        expectedClass = failureClass,
        correctAction = failureClass.expectedAction,
        generator = provenance.generator,
        reviewed = provenance.reviewed,
        split = split,
    )

    private data class CaseProvenance(
        val generator: String,
        val reviewed: Boolean,
    )
}
