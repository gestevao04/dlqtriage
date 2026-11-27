package dev.dlqtriage.application.agent

import dev.dlqtriage.application.port.input.InvestigateDlqMessageUseCase
import dev.dlqtriage.application.port.output.AgentDecisionParseResult
import dev.dlqtriage.application.port.output.AgentDecisionParserPort
import dev.dlqtriage.application.port.output.AgentRunRepository
import dev.dlqtriage.application.port.output.AgentToolResult
import dev.dlqtriage.application.port.output.InvestigatorPromptPort
import dev.dlqtriage.application.port.output.LlmCompletionResult
import dev.dlqtriage.application.port.output.LlmMessage
import dev.dlqtriage.application.port.output.LlmPort
import dev.dlqtriage.application.port.output.LlmRole
import dev.dlqtriage.application.port.output.LlmToolDefinition
import dev.dlqtriage.application.port.output.ReadOnlyAgentTool
import dev.dlqtriage.domain.agent.AgentDecision
import dev.dlqtriage.domain.agent.AgentInvestigation
import dev.dlqtriage.domain.agent.AgentRun
import dev.dlqtriage.domain.agent.AgentRunStatus
import dev.dlqtriage.domain.agent.AgentStep
import dev.dlqtriage.domain.classification.ExpectedAction
import dev.dlqtriage.domain.ingestion.DlqMessage
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class InvestigateDlqMessageService(
    private val llmPort: LlmPort,
    private val tools: List<ReadOnlyAgentTool>,
    private val promptBuilder: InvestigatorPromptPort,
    private val decisionParser: AgentDecisionParserPort,
    private val agentRunRepository: AgentRunRepository,
    private val clock: Clock,
) : InvestigateDlqMessageUseCase {
    override fun investigate(message: DlqMessage): AgentInvestigation {
        val context = createContext(message)
        runAgentLoop(context)
        val investigation = context.toInvestigation()
        agentRunRepository.save(investigation)
        return investigation
    }

    private fun createContext(message: DlqMessage): InvestigationContext {
        val prompt = promptBuilder.build(message)
        return InvestigationContext(
            runId = UUID.randomUUID(),
            message = message,
            promptVersion = prompt.version,
            startedAt = clock.instant(),
            messages =
                mutableListOf(
                    LlmMessage(LlmRole.SYSTEM, prompt.system),
                    LlmMessage(LlmRole.USER, prompt.user),
                ),
            toolMap = tools.associateBy { it.definition.name },
            toolDefinitions = tools.map(ReadOnlyAgentTool::definition),
        )
    }

    private fun runAgentLoop(context: InvestigationContext) {
        while (context.steps.size < MAX_AGENT_STEPS && context.decision == null) {
            val completionStartedAt = clock.instant()
            when (val completion = llmPort.complete(context.messages, context.toolDefinitions)) {
                is LlmCompletionResult.Failure -> recordLlmFailure(context, completion, completionStartedAt)
                is LlmCompletionResult.Success -> handleCompletion(context, completion, completionStartedAt)
            }
        }
        if (context.decision == null) {
            context.decision = AgentDecision.escalate(stepLimitReason())
        }
    }

    private fun recordLlmFailure(
        context: InvestigationContext,
        failure: LlmCompletionResult.Failure,
        startedAt: Instant,
    ) {
        context.decision = AgentDecision.escalate("LLM investigation failed: ${failure.reason}")
        recordStep(
            context,
            StepDetails(
                toolCalled = null,
                result = "LLM failure recorded; investigation escalated.",
                inputTokens = 0,
                outputTokens = 0,
                startedAt = startedAt,
            ),
        )
    }

    private fun handleCompletion(
        context: InvestigationContext,
        completion: LlmCompletionResult.Success,
        startedAt: Instant,
    ) {
        context.inputTokens += completion.inputTokens
        context.outputTokens += completion.outputTokens
        if (completion.toolCalls.isEmpty()) {
            recordFinalDecision(context, completion, startedAt)
        } else {
            executeToolCalls(context, completion, startedAt)
        }
    }

    private fun recordFinalDecision(
        context: InvestigationContext,
        completion: LlmCompletionResult.Success,
        startedAt: Instant,
    ) {
        context.decision =
            when (val parsed = decisionParser.parse(completion.content)) {
                is AgentDecisionParseResult.Valid -> parsed.decision
                is AgentDecisionParseResult.Invalid -> AgentDecision.escalate(parsed.reason)
            }
        recordStep(
            context,
            StepDetails(
                toolCalled = null,
                result = context.decision.summarizedResult(),
                inputTokens = completion.inputTokens,
                outputTokens = completion.outputTokens,
                startedAt = startedAt,
            ),
        )
    }

    private fun executeToolCalls(
        context: InvestigationContext,
        completion: LlmCompletionResult.Success,
        completionStartedAt: Instant,
    ) {
        context.messages +=
            LlmMessage(
                role = LlmRole.ASSISTANT,
                content = completion.content,
                toolCalls = completion.toolCalls,
            )
        completion.toolCalls.forEachIndexed { index, toolCall ->
            if (context.steps.size >= MAX_AGENT_STEPS) {
                context.decision = AgentDecision.escalate(stepLimitReason())
                return
            }
            val toolStartedAt = clock.instant()
            val outcome = executeTool(context.toolMap[toolCall.name], toolCall.name, toolCall.arguments)
            context.messages +=
                LlmMessage(
                    role = LlmRole.TOOL,
                    content = outcome.contentForLlm().inUntrustedBlock(),
                    toolCallId = toolCall.id,
                    toolName = toolCall.name,
                )
            recordStep(
                context,
                StepDetails(
                    toolCalled = toolCall.name,
                    result = outcome.summaryForAudit(),
                    inputTokens = if (index == 0) completion.inputTokens else 0,
                    outputTokens = if (index == 0) completion.outputTokens else 0,
                    startedAt = if (index == 0) completionStartedAt else toolStartedAt,
                ),
            )
        }
    }

    private fun executeTool(
        tool: ReadOnlyAgentTool?,
        toolName: String,
        arguments: String,
    ): AgentToolResult =
        if (tool == null) {
            AgentToolResult.Failure("Unknown read-only tool: $toolName")
        } else {
            try {
                tool.execute(arguments)
            } catch (exception: DataAccessException) {
                logger.error("Read-only agent tool failed: {}", toolName, exception)
                AgentToolResult.Failure("Tool execution failed: $toolName")
            } catch (exception: IOException) {
                logger.error("Read-only agent tool failed: {}", toolName, exception)
                AgentToolResult.Failure("Tool execution failed: $toolName")
            } catch (exception: IllegalStateException) {
                logger.error("Read-only agent tool failed: {}", toolName, exception)
                AgentToolResult.Failure("Tool execution failed: $toolName")
            }
        }

    private fun recordStep(
        context: InvestigationContext,
        details: StepDetails,
    ) {
        context.steps +=
            AgentStep(
                runId = context.runId,
                sequence = context.steps.size + 1,
                summarizedInput =
                    if (details.toolCalled == null) {
                        "Investigator request and message context."
                    } else {
                        "Read-only tool request: ${details.toolCalled}."
                    },
                toolCalled = details.toolCalled,
                summarizedResult = details.result.take(MAX_SUMMARY_LENGTH).ifBlank { "No result summary." },
                inputTokens = details.inputTokens.coerceAtLeast(0),
                outputTokens = details.outputTokens.coerceAtLeast(0),
                durationMillis = Duration.between(details.startedAt, clock.instant()).toMillis().coerceAtLeast(0),
            )
    }

    private fun InvestigationContext.toInvestigation(): AgentInvestigation {
        val finalDecision = decision ?: AgentDecision.escalate(stepLimitReason())
        val completedAt = clock.instant()
        val run =
            AgentRun(
                id = runId,
                messageId = message.id,
                promptVersion = promptVersion,
                decision = finalDecision,
                status =
                    if (finalDecision.suggestedAction == ExpectedAction.ESCALATE) {
                        AgentRunStatus.ESCALATED
                    } else {
                        AgentRunStatus.COMPLETED
                    },
                startedAt = startedAt,
                completedAt = completedAt,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
            )
        return AgentInvestigation(run, steps.toList())
    }

    private data class StepDetails(
        val toolCalled: String?,
        val result: String,
        val inputTokens: Int,
        val outputTokens: Int,
        val startedAt: Instant,
    )

    private data class InvestigationContext(
        val runId: UUID,
        val message: DlqMessage,
        val promptVersion: String,
        val startedAt: Instant,
        val messages: MutableList<LlmMessage>,
        val toolMap: Map<String, ReadOnlyAgentTool>,
        val toolDefinitions: List<LlmToolDefinition>,
        val steps: MutableList<AgentStep> = mutableListOf(),
        var inputTokens: Int = 0,
        var outputTokens: Int = 0,
        var decision: AgentDecision? = null,
    )

    companion object {
        const val MAX_AGENT_STEPS = 6
        private const val MAX_SUMMARY_LENGTH = 500
        private val logger = LoggerFactory.getLogger(InvestigateDlqMessageService::class.java)
    }
}

private fun AgentDecision?.summarizedResult(): String =
    when {
        this == null -> "No structured decision was produced."
        failureReason != null -> "Investigation escalated: $failureReason"
        else -> "Structured decision produced: $classification."
    }

private fun AgentToolResult.contentForLlm(): String =
    when (this) {
        is AgentToolResult.Success -> content
        is AgentToolResult.Failure -> """{"error":"${reason.jsonEscaped()}"}"""
    }

private fun AgentToolResult.summaryForAudit(): String =
    when (this) {
        is AgentToolResult.Success -> summary
        is AgentToolResult.Failure -> "Tool failed: $reason"
    }

private fun String.inUntrustedBlock(): String {
    val escaped = replace("<", "\\u003c").replace(">", "\\u003e")
    return "<UNTRUSTED_DATA>\n$escaped\n</UNTRUSTED_DATA>"
}

private fun String.jsonEscaped(): String =
    replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

private fun stepLimitReason() = "Investigation exceeded the ${InvestigateDlqMessageService.MAX_AGENT_STEPS}-step limit."
