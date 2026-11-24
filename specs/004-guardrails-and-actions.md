# Spec 005 — Observability and audit
Status: Draft | Depends on: 003 (ideally 004)

## Context
Operating and debugging an agent requires seeing every run: steps, cost,
latency and decisions. This spec standardizes traces, metrics and replay.

## User story
As a developer, I want to trace and replay the agent's reasoning, so I can
debug wrong decisions and track cost and quality.

## Acceptance criteria
- [ ] One trace per message; one span per agent step and per tool call, with
  attributes `run.id`, `message.id`, `tool.name`.
- [ ] Traces exported via OTLP and visible in local Jaeger.
- [ ] Micrometer metrics on `/actuator/prometheus`: `dlq.messages.consumed`,
  `dlq.decision.count{action,class}`, `agent.run.duration`,
  `agent.steps`, `agent.tokens`, `agent.cost`.
- [ ] Structured logs with `traceId`, `messageId`, `runId`.
- [ ] No INFO log contains a full payload (automated test).
- [ ] A replay command/endpoint re-executes a recorded `agent_run` with the
  fake LLM, reproducing the recorded responses.
- [ ] Health checks: database, Kafka, LLM.
- [ ] README includes 3 example queries/panels for the metrics.

## Out of scope
Alerting, production dashboards, long-term retention.

## Technical notes and dependencies
- Step recording exists from spec 003; this spec adds correlation and export.
- Cost = tokens x configurable price table (0 on `local`).

## Test plan
- Unit: cost calculation, log masking.
- Integration: spans/metrics exist after a run; replay.
- Manual: open Jaeger and follow one message from consumption to decision.

## DoD
Criteria ticked; a full trace of one message demonstrated; tests and lint
green.