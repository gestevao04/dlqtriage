package dev.dlqtriage.evals

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.dlqtriage.DlqtriageApplication
import dev.dlqtriage.adapter.agent.InvestigatorPromptBuilder
import dev.dlqtriage.adapter.output.llm.JacksonAgentDecisionParser
import dev.dlqtriage.application.agent.InvestigateDlqMessageService
import dev.dlqtriage.application.classification.RuleBasedClassifier
import dev.dlqtriage.application.port.input.Classifier
import dev.dlqtriage.application.port.input.InvestigateDlqMessageUseCase
import dev.dlqtriage.application.port.output.AgentRunRepository
import dev.dlqtriage.application.port.output.MessageRepository
import dev.dlqtriage.application.port.output.SaveMessageResult
import dev.dlqtriage.domain.agent.AgentInvestigation
import dev.dlqtriage.domain.classification.ExpectedAction
import dev.dlqtriage.domain.classification.FailureClass
import dev.dlqtriage.domain.ingestion.DlqMessage
import dev.dlqtriage.domain.ingestion.DlqMessageId
import dev.dlqtriage.domain.ingestion.DlqMessageStatus
import dev.dlqtriage.domain.ingestion.PayloadEncoding
import org.springframework.boot.builder.SpringApplicationBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

private const val MIN_DATASET_CASES = 100
private const val MIN_CASES_PER_CLASS = 10
private const val MIN_MODEL_GENERATORS = 2
private const val MIN_REVIEWED_RATIO = 0.2
private const val MIN_ARGUMENT_COUNT = 2
private const val MAX_ARGUMENT_COUNT = 3
private const val MODE_ARGUMENT_INDEX = 2

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

data class InvestigatorEvaluationMetrics(
    val cases: Int,
    val accuracy: Double,
    val averageSteps: Double,
    val averageLatencyMillis: Double,
    val perClassAccuracy: Map<String, Double>,
    val misclassifications: List<InvestigatorMisclassificationSummary>,
)

data class InvestigatorMisclassificationSummary(
    val caseId: String,
    val expectedClass: FailureClass,
    val predictedClass: FailureClass,
)

data class BaselineVsInvestigatorReport(
    val baseline: ClassificationMetrics,
    val investigator: InvestigatorEvaluationMetrics,
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

private fun EvaluationCase.toDlqMessage() =
    DlqMessage(
        id = DlqMessageId(UUID.nameUUIDFromBytes(id.toByteArray())),
        messageKey = null,
        sourceTopic = "eval",
        partition = 0,
        offset = 0,
        payload = "",
        payloadEncoding = PayloadEncoding.TEXT,
        payloadInvalid = false,
        headers = emptyMap(),
        errorClass = errorClass,
        errorMessage = errorMessage,
        stackTrace = stackTrace,
        receivedAt = Instant.EPOCH,
        status = DlqMessageStatus.RECEIVED,
    )

private fun predict(
    cases: List<EvaluationCase>,
    classifier: Classifier,
): List<Prediction> =
    cases.map { case ->
        val result = classifier.classify(case.toDlqMessage())
        Prediction(case, result.failureClass, result.evidence)
    }

fun buildComparisonReport(
    cases: List<EvaluationCase>,
    baselineClassifier: Classifier,
    investigator: (DlqMessage) -> AgentInvestigation,
): BaselineVsInvestigatorReport {
    require(cases.isNotEmpty()) { "Evaluation dataset must not be empty" }
    val baseline = buildReport(cases, baselineClassifier).overall
    val agentPredictions =
        cases.map { case ->
            val result = investigator(case.toDlqMessage())
            Triple(case, result.run.decision.classification, result)
        }
    val perClassAccuracy =
        FailureClass.entries.associate { failureClass ->
            val classCases = agentPredictions.filter { it.first.expectedClass == failureClass }
            failureClass.name to
                if (classCases.isEmpty()) {
                    0.0
                } else {
                    classCases.count { it.first.expectedClass == it.second }.toDouble() / classCases.size
                }
        }
    val total = agentPredictions.size
    return BaselineVsInvestigatorReport(
        baseline = baseline,
        investigator =
            InvestigatorEvaluationMetrics(
                cases = total,
                accuracy = agentPredictions.count { it.first.expectedClass == it.second }.toDouble() / total,
                averageSteps = agentPredictions.sumOf { it.third.steps.size }.toDouble() / total,
                averageLatencyMillis =
                    agentPredictions
                        .map { Duration.between(it.third.run.startedAt, it.third.run.completedAt).toMillis() }
                        .average(),
                perClassAccuracy = perClassAccuracy,
                misclassifications =
                    agentPredictions
                        .filter { it.first.expectedClass != it.second }
                        .map { (case, predicted, _) ->
                            InvestigatorMisclassificationSummary(
                                caseId = case.id,
                                expectedClass = case.expectedClass,
                                predictedClass = predicted,
                            )
                        },
            ),
    )
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
    require(args.size in MIN_ARGUMENT_COUNT..MAX_ARGUMENT_COUNT) {
        "Usage: evalRun <dataset.jsonl> <report.json> [fake|local]"
    }
    val datasetPath = Path.of(args[0])
    val reportPath = Path.of(args[1])
    val llmMode = args.getOrNull(MODE_ARGUMENT_INDEX) ?: "fake"
    require(llmMode == "fake" || llmMode == "local") { "evalRun mode must be fake or local." }
    val mapper = jacksonObjectMapper()
    val cases =
        Files
            .readAllLines(datasetPath)
            .filter(String::isNotBlank)
            .map { mapper.readValue<EvaluationCase>(it) }

    validateDataset(cases)
    val classifier = RuleBasedClassifier()
    val report =
        if (llmMode == "fake") {
            val fakeLlm = EvaluationLlmAdapter(classifier, mapper)
            val investigator =
                InvestigateDlqMessageService(
                    llmPort = fakeLlm,
                    tools = emptyList(),
                    promptBuilder = InvestigatorPromptBuilder(mapper),
                    decisionParser = JacksonAgentDecisionParser(mapper),
                    agentRunRepository = AgentRunRepository { },
                    clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                )
            buildComparisonReport(cases, classifier, investigator::investigate)
        } else {
            evaluateLocally(cases, classifier)
        }
    Files.createDirectories(reportPath.parent)
    Files.writeString(
        reportPath,
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + System.lineSeparator(),
    )
    println("Wrote baseline and investigator results for ${report.investigator.cases} cases to $reportPath")
}

private fun evaluateLocally(
    cases: List<EvaluationCase>,
    classifier: RuleBasedClassifier,
): BaselineVsInvestigatorReport {
    val localCases =
        FailureClass.entries.flatMap { failureClass ->
            cases.filter { it.expectedClass == failureClass }.take(2)
        }
    val context =
        SpringApplicationBuilder(DlqtriageApplication::class.java)
            .profiles("local")
            .properties(
                mapOf(
                    "spring.kafka.listener.auto-startup" to false,
                    "spring.main.web-application-type" to "none",
                ),
            ).run()
    return context.use { applicationContext ->
        val investigator = applicationContext.getBean(InvestigateDlqMessageUseCase::class.java)
        val messageRepository = applicationContext.getBean(MessageRepository::class.java)
        val sourceTopic = "dlq-eval-${UUID.randomUUID()}"
        var offset = 0L
        buildComparisonReport(localCases, classifier) { evaluationMessage ->
            val message =
                evaluationMessage.copy(
                    id = DlqMessageId(UUID.randomUUID()),
                    sourceTopic = sourceTopic,
                    offset = offset++,
                    payload = """{"syntheticCase":"${evaluationMessage.id.value}"}""",
                )
            val persistedMessage =
                when (val saved = messageRepository.save(message)) {
                    is SaveMessageResult.Saved -> saved.message
                    is SaveMessageResult.Duplicate -> saved.message
                }
            investigator.investigate(persistedMessage)
        }
    }
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
