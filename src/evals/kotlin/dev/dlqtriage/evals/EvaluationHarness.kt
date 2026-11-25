package dev.dlqtriage.evals

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.dlqtriage.application.classification.RuleBasedClassifier
import dev.dlqtriage.application.port.input.Classifier
import dev.dlqtriage.domain.classification.ExpectedAction
import dev.dlqtriage.domain.classification.FailureClass
import dev.dlqtriage.domain.ingestion.DlqMessage
import dev.dlqtriage.domain.ingestion.DlqMessageId
import dev.dlqtriage.domain.ingestion.DlqMessageStatus
import dev.dlqtriage.domain.ingestion.PayloadEncoding
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

private const val MIN_DATASET_CASES = 100
private const val MIN_CASES_PER_CLASS = 10
private const val MIN_MODEL_GENERATORS = 2
private const val MIN_REVIEWED_RATIO = 0.2

data class EvaluationCase(
    val id: String,
    val errorClass: String,
    val errorMessage: String?,
    val stackTrace: String?,
    val expectedClass: FailureClass,
    val correctAction: ExpectedAction,
    val generator: String,
    val reviewed: Boolean,
    val split: String,
)

data class ClassificationMetrics(
    val cases: Int,
    val accuracy: Double,
    val perClassAccuracy: Map<String, Double>,
    val confusionMatrix: Map<String, Map<String, Int>>,
)

data class EvaluationReport(
    val classifierVersion: String,
    val datasetCases: Int,
    val reviewedCases: Int,
    val reviewedRatio: Double,
    val generators: List<String>,
    val datasetRequirementsMet: Boolean,
    val overall: ClassificationMetrics,
    val perSplit: Map<String, ClassificationMetrics>,
    val misclassifications: List<MisclassificationSummary>,
)

data class MisclassificationSummary(
    val caseId: String,
    val expectedClass: FailureClass,
    val predictedClass: FailureClass,
    val errorClass: String,
    val errorMessage: String?,
    val evidence: List<String>,
)

fun evaluate(
    cases: List<EvaluationCase>,
    classifier: Classifier,
): ClassificationMetrics = calculateMetrics(predict(cases, classifier))

private data class Prediction(
    val case: EvaluationCase,
    val predictedClass: FailureClass,
    val evidence: List<String>,
)

private fun predict(
    cases: List<EvaluationCase>,
    classifier: Classifier,
): List<Prediction> =
    cases.map { case ->
        val message =
            DlqMessage(
                id = DlqMessageId(UUID.nameUUIDFromBytes(case.id.toByteArray())),
                messageKey = null,
                sourceTopic = "eval",
                partition = 0,
                offset = 0,
                payload = "",
                payloadEncoding = PayloadEncoding.TEXT,
                payloadInvalid = false,
                headers = emptyMap(),
                errorClass = case.errorClass,
                errorMessage = case.errorMessage,
                stackTrace = case.stackTrace,
                receivedAt = Instant.EPOCH,
                status = DlqMessageStatus.RECEIVED,
            )
        val result = classifier.classify(message)
        Prediction(case, result.failureClass, result.evidence)
    }

private fun calculateMetrics(predictions: List<Prediction>): ClassificationMetrics {
    val cases = predictions.map(Prediction::case)
    val classes = FailureClass.entries.map(FailureClass::name)
    val confusion =
        classes.associateWith { actual ->
            classes.associateWith { predictedClass ->
                predictions.count { prediction ->
                    prediction.case.expectedClass.name == actual &&
                        prediction.predictedClass.name == predictedClass
                }
            }
        }
    val perClass =
        classes.associateWith { actual ->
            val classCases = predictions.filter { it.case.expectedClass.name == actual }
            if (classCases.isEmpty()) {
                0.0
            } else {
                classCases.count { it.case.expectedClass == it.predictedClass }.toDouble() / classCases.size
            }
        }

    return ClassificationMetrics(
        cases = cases.size,
        accuracy =
            if (cases.isEmpty()) {
                0.0
            } else {
                predictions.count { it.case.expectedClass == it.predictedClass }.toDouble() / cases.size
            },
        perClassAccuracy = perClass,
        confusionMatrix = confusion,
    )
}

fun buildReport(
    cases: List<EvaluationCase>,
    classifier: Classifier,
): EvaluationReport {
    require(cases.isNotEmpty()) { "Evaluation dataset must not be empty" }
    require(cases.map(EvaluationCase::id).distinct().size == cases.size) { "Dataset case ids must be unique" }
    require(cases.all { it.correctAction == it.expectedClass.expectedAction }) {
        "Each case's correctAction must match its expected class"
    }

    val generators = cases.map(EvaluationCase::generator).distinct().sorted()
    val modelGenerators = generators.filterNot { it == "human" }
    val reviewedRatio = cases.count(EvaluationCase::reviewed).toDouble() / cases.size
    val classCounts = cases.groupingBy(EvaluationCase::expectedClass).eachCount()
    val datasetRequirementsMet =
        cases.size >= MIN_DATASET_CASES &&
            FailureClass.entries.all { (classCounts[it] ?: 0) >= MIN_CASES_PER_CLASS } &&
            modelGenerators.distinct().size >= MIN_MODEL_GENERATORS &&
            "human" in generators &&
            reviewedRatio >= MIN_REVIEWED_RATIO &&
            cases.any { it.split == "train" } &&
            cases.any { it.split == "held-out" }

    val predictions = predict(cases, classifier)
    return EvaluationReport(
        classifierVersion = (classifier as? RuleBasedClassifier)?.let { RuleBasedClassifier.VERSION } ?: "custom",
        datasetCases = cases.size,
        reviewedCases = cases.count(EvaluationCase::reviewed),
        reviewedRatio = reviewedRatio,
        generators = generators,
        datasetRequirementsMet = datasetRequirementsMet,
        overall = calculateMetrics(predictions),
        perSplit =
            predictions
                .groupBy { it.case.split }
                .toSortedMap()
                .mapValues { (_, splitPredictions) -> calculateMetrics(splitPredictions) },
        misclassifications =
            predictions
                .filter { it.case.expectedClass != it.predictedClass }
                .map { prediction ->
                    MisclassificationSummary(
                        caseId = prediction.case.id,
                        expectedClass = prediction.case.expectedClass,
                        predictedClass = prediction.predictedClass,
                        errorClass = prediction.case.errorClass,
                        errorMessage = prediction.case.errorMessage,
                        evidence = prediction.evidence,
                    )
                },
    )
}

fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: evalRun <dataset.jsonl> <report.json>" }
    val datasetPath = Path.of(args[0])
    val reportPath = Path.of(args[1])
    val mapper = jacksonObjectMapper()
    val cases =
        Files
            .readAllLines(datasetPath)
            .filter(String::isNotBlank)
            .map { mapper.readValue<EvaluationCase>(it) }

    validateDataset(cases)
    val report = buildReport(cases, RuleBasedClassifier())
    Files.createDirectories(reportPath.parent)
    Files.writeString(
        reportPath,
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + System.lineSeparator(),
    )
    println("Wrote ${cases.size} evaluation cases to $reportPath")
}

private fun validateDataset(cases: List<EvaluationCase>) {
    require(cases.size >= MIN_DATASET_CASES) {
        "Dataset must contain at least $MIN_DATASET_CASES labeled cases"
    }
    val classCounts = cases.groupingBy(EvaluationCase::expectedClass).eachCount()
    val underrepresented =
        FailureClass.entries.filter { (classCounts[it] ?: 0) < MIN_CASES_PER_CLASS }
    require(underrepresented.isEmpty()) {
        "Dataset requires at least $MIN_CASES_PER_CLASS cases per class: $underrepresented"
    }
}
