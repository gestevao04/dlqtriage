package dev.dlqtriage.application.classification

import dev.dlqtriage.application.port.input.Classifier
import dev.dlqtriage.domain.classification.Classification
import dev.dlqtriage.domain.classification.FailureClass
import dev.dlqtriage.domain.ingestion.DlqMessage

class RuleBasedClassifier : Classifier {
    override fun classify(message: DlqMessage): Classification {
        val matched =
            RULES
                .flatMap { rule ->
                    rule.patterns.flatMap { (field, pattern) ->
                        val value = field.value(message)
                        pattern.findAll(value).map { match ->
                            Match(
                                failureClass = rule.failureClass,
                                priority = field.priority,
                                evidence = "${field.label}: ${match.value}",
                            )
                        }
                    }
                }

        val winner =
            matched.maxWithOrNull(
                compareBy<Match> { it.priority }
                    .thenBy { -RULE_PRIORITY.indexOf(it.failureClass) },
            )

        if (winner == null) {
            return Classification(
                failureClass = FailureClass.UNKNOWN,
                confidence = UNKNOWN_CONFIDENCE,
                evidence = emptyList(),
                classifierVersion = VERSION,
            )
        }

        val evidence =
            matched
                .filter { it.failureClass == winner.failureClass }
                .map(Match::evidence)
                .distinct()

        return Classification(
            failureClass = winner.failureClass,
            confidence = confidence(winner.priority),
            evidence = evidence,
            classifierVersion = VERSION,
        )
    }

    private enum class Field(
        val label: String,
        val priority: Int,
        val value: (DlqMessage) -> String,
    ) {
        ERROR_CLASS("errorClass", ERROR_CLASS_PRIORITY, { it.errorClass }),
        ERROR_MESSAGE("errorMessage", ERROR_MESSAGE_PRIORITY, { it.errorMessage.orEmpty() }),
        STACK_TRACE("stackTrace", STACK_TRACE_PRIORITY, { it.stackTrace.orEmpty() }),
    }

    private data class Rule(
        val failureClass: FailureClass,
        val patterns: List<Pair<Field, Regex>>,
    )

    private data class Match(
        val failureClass: FailureClass,
        val priority: Int,
        val evidence: String,
    )

    companion object {
        const val VERSION = "baseline-v1"
        private const val UNKNOWN_CONFIDENCE = 0.5
        private const val ERROR_CLASS_PRIORITY = 3
        private const val ERROR_MESSAGE_PRIORITY = 2
        private const val STACK_TRACE_PRIORITY = 1
        private const val ERROR_CLASS_CONFIDENCE = 0.99
        private const val ERROR_MESSAGE_CONFIDENCE = 0.9
        private const val STACK_TRACE_CONFIDENCE = 0.8

        private val RULE_PRIORITY =
            listOf(
                FailureClass.TRANSIENT_INFRASTRUCTURE,
                FailureClass.SCHEMA_MISMATCH,
                FailureClass.VALIDATION_ERROR,
                FailureClass.PERMANENT_BUSINESS_ERROR,
            )

        private val RULES =
            listOf(
                Rule(
                    FailureClass.TRANSIENT_INFRASTRUCTURE,
                    patterns(
                        Field.ERROR_CLASS to
                            """(?ix)
                                (?: Timeout | Connect | Socket | Transient | ServiceUnavailable | Retriable )
                                [\w.$]* Exception
                            """,
                        Field.ERROR_MESSAGE to
                            """(?ix)
                                \b(?: timed? \s+ out | temporarily \s+ unavailable | connection \s+ reset |
                                connection \s+ refused | broker \s+ not \s+ available | try \s+ again )\b
                            """,
                        Field.STACK_TRACE to
                            """(?ix)
                                (?: SocketTimeoutException | ConnectException | TimeoutException | RetriableException )
                            """,
                    ),
                ),
                Rule(
                    FailureClass.SCHEMA_MISMATCH,
                    patterns(
                        Field.ERROR_CLASS to
                            """(?ix)
                                (?: Schema | JsonMapping | Serialization | AvroType | Protobuf )
                                [\w.$]* (?: Exception | Error )?
                            """,
                        Field.ERROR_MESSAGE to
                            """(?ix)
                                \b(?: schema \s+ mismatch | unknown \s+ field | cannot \s+ deserialize |
                                failed \s+ to \s+ deserialize | incompatible \s+ schema | invalid \s+ schema )\b
                            """,
                        Field.STACK_TRACE to
                            """(?ix)
                                (?: JsonMappingException | SerializationException | AvroTypeException | SchemaRegistry )
                            """,
                    ),
                ),
                Rule(
                    FailureClass.VALIDATION_ERROR,
                    patterns(
                        Field.ERROR_CLASS to
                            """(?ix)
                                (?: ConstraintViolation | MethodArgumentNotValid | Validation )
                                [\w.$]* (?: Exception )?
                            """,
                        Field.ERROR_MESSAGE to
                            """(?ix)
                                \b(?: validation \s+ failed | invalid \s+ (?: value | field | argument ) |
                                must \s+ not \s+ be \s+ blank | required \s+ field |
                                does \s+ not \s+ satisfy \s+ constraint )\b
                            """,
                        Field.STACK_TRACE to
                            """(?ix)
                                (?: ConstraintViolationException | MethodArgumentNotValidException | Validator )
                            """,
                    ),
                ),
                Rule(
                    FailureClass.PERMANENT_BUSINESS_ERROR,
                    patterns(
                        Field.ERROR_CLASS to
                            """(?ix)
                                (?: BusinessRule | InsufficientFunds | OrderNotFound | PermanentBusiness )
                                [\w.$]* (?: Exception )?
                            """,
                        Field.ERROR_MESSAGE to
                            """(?ix)
                                \b(?: insufficient \s+ funds | order \s+ not \s+ found |
                                business \s+ rule \s+ violated | account \s+ is \s+ closed |
                                payment \s+ declined | customer \s+ is \s+ inactive )\b
                            """,
                        Field.STACK_TRACE to
                            """(?ix)
                                (?: BusinessRuleException | InsufficientFundsException | OrderNotFoundException )
                            """,
                    ),
                ),
            )

        private fun patterns(vararg patterns: Pair<Field, String>): List<Pair<Field, Regex>> =
            patterns.map { (field, pattern) -> field to Regex(pattern) }

        private fun confidence(priority: Int): Double =
            when (priority) {
                ERROR_CLASS_PRIORITY -> ERROR_CLASS_CONFIDENCE
                ERROR_MESSAGE_PRIORITY -> ERROR_MESSAGE_CONFIDENCE
                else -> STACK_TRACE_CONFIDENCE
            }
    }
}
