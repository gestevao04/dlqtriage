# Architecture

## Main flow
Kafka (DLQ topic) -> KafkaDlqListener (adapter/in)
-> IngestDlqMessageUseCase (dedup by topic/partition/offset, persist)
-> ClassifyFailureUseCase (baseline) -> InvestigateFailureUseCase (agent)
-> GuardrailPolicy (domain) -> DecideActionUseCase
├─ NEEDS_APPROVAL: approval queue -> human approves (REST or MCP)
│     -> ExecuteActionUseCase -> ReprocessPort (republish)
├─ AUTO (autonomy=AUTO only): ExecuteActionUseCase -> ReprocessPort
└─ ESCALATE: record diagnosis for a human

Where automatic reprocessing happens: `ExecuteActionUseCase` is the single
place that calls `ReprocessPort`. It is reached either after a human
approval or, when `agent.autonomy=AUTO`, directly from `GuardrailPolicy`.
Nothing else may republish a message.

## Autonomy modes
- OFF: classify only; no recommendation.
- SUGGEST (default): the agent records a recommended action; nothing runs
  without human approval.
- AUTO: low-risk, high-confidence actions run without approval, still
  bounded by the guardrails.

## Ports (application/port/out)
DlqSource, MessageRepository, DiagnosisRepository, AgentRunRepository,
LlmPort, InvestigationToolPort (read-only), ReprocessPort.

## Data model (high level; detail in specs)
dlq_message, classification, agent_run, agent_step, decision, approval,
diagnosis_cache (PostgreSQL; no Redis in the MVP).

## Failure taxonomy (ASSUMPTION, refined in spec 002)
SCHEMA_INVALID, DESERIALIZATION_ERROR, TRANSIENT_DEPENDENCY, DUPLICATE,
DATA_CORRUPTION, SUSPECTED_BUG, UNKNOWN.

## Actions
REPROCESS, DISCARD_WITH_REASON, ESCALATE.

## Open decisions
Recorded as ADRs in docs/adr/ when made.