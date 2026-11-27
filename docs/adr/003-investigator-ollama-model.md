# ADR 003: Local investigator model

Status: Provisional

## Context

The investigator requires local tool calling and structured JSON output. The
local profile must remain offline from hosted model APIs and the eval harness
must remain deterministic.

## Decision

Use the Qwen 3 4B Ollama model configured as `qwen3:4b` for the local profile.
Keep the Spring AI/Ollama adapter behind `LlmPort`; tests and CI use the
programmable fake, and the eval harness uses its deterministic fake.

The agent exposes only the four read-only investigator tools. It has a
provisional six-step limit, validates the final output, and escalates if the
model fails, exceeds the limit, or returns invalid JSON.

## Verification

The prompt and tool-call contract are covered by automated tests. A manual
run of 10 sandbox messages against local Ollama is required before relying on
the model's tool-calling quality.

The manual run completed for 10 synthetic messages. The baseline scored 7/10
(70% accuracy); the investigator scored 4/10 (40%) with 45,343.5 ms average
latency. The model called no read-only tools. Two decisions were valid
transient-infrastructure recommendations; the other eight safely escalated
because the output was invalid or recommended `REPROCESS` for a non-transient
classification. The output schema and fail-safe validator prevented those
unsafe recommendations from being accepted.

Conclusion: `qwen3:4b` is not reliable enough for use beyond local
experimentation. Keep the local profile fail-safe and do not rely on its
diagnoses until a better model or prompt/tool-calling configuration is
validated. No hosted model was evaluated.
