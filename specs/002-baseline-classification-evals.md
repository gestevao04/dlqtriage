# Spec 002 — Baseline classification and eval harness
Status: Approved | Depends on: 001

## Context
Before any agent exists we need a deterministic baseline and a way to
measure quality. This spec delivers the rule-based classifier, the sandbox
failure generator and the eval harness that will be the yardstick for the
agent.

## User story
As a tech lead, I want to measure classification quality objectively, so I
know whether the agent really beats simple rules.

## Acceptance criteria
- [ ] `FailureClass` taxonomy and expected actions defined in the domain.
- [ ] `RuleBasedClassifier` classifies by error class/message/stack trace
  patterns and returns class + evidence (the matched snippets).
- [ ] Results persisted in `classification` (message_id, class, confidence,
  evidence, classifier_version).
- [ ] The sandbox generates synthetic failures for all classes and publishes
  them to the DLQ topic.
- [ ] `evals/dataset/*.jsonl` has >= 100 labeled cases (class and correct
  action), >= 10 per class.
- [ ] Each case records provenance: `generator` (model or "human") and
  `reviewed` (boolean). Cases come from at least 2 different LLMs plus
  hand-written ones.
- [ ] At least 20% of cases are human-reviewed; a held-out split is never
  used while tuning rules or prompts.
- [ ] The harness runs a `Classifier` over the dataset and writes a report
  (overall and per-class accuracy, confusion matrix) to `evals/reports/`.
- [ ] `./gradlew evalRun` is deterministic and needs no network or LLM.
- [ ] The baseline report is committed.

## Out of scope
LLM usage, tools, action decisions.

## Technical notes and dependencies
- `Classifier` interface so the agent plugs into the same harness.
- Dataset generation scripts/prompts live in `evals/` and are documented.
- Sandbox lives in `src/sandbox`, outside the shipped artifact.
- No real payloads: all data is synthetic (public repo).

## Test plan
- Unit: each rule with positive and negative cases.
- Integration: classify messages coming from the spec 001 consumer.
- Manual: inspect the report and 10 misclassifications.

## DoD
Criteria ticked; baseline published; dataset reviewed; lint and tests green.