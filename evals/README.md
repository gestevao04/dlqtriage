# Baseline classification evaluations

The dataset is synthetic JSONL in `dataset/baseline.jsonl`. Each record
contains the observed exception metadata, expected class and action, source
model (or `human` for hand-written cases), review status, and a `train` or
`held-out` split. The held-out split must not be used to tune rules or prompts.

Run `./gradlew evalRun` to compare the deterministic `RuleBasedClassifier`
baseline with the investigator running against the deterministic fake LLM. It
needs no network or LLM service and writes baseline accuracy alongside
investigator accuracy, average steps, latency, per-class accuracy, and
misclassified case IDs to `reports/investigator.json`. The checked-in
`reports/baseline.json` remains the baseline-only report. Neither report copies
message payloads.

The baseline taxonomy maps `TRANSIENT_INFRASTRUCTURE` to `REPROCESS`.
`SCHEMA_MISMATCH`, `VALIDATION_ERROR`, `PERMANENT_BUSINESS_ERROR`, and
`UNKNOWN` map to `ESCALATE`. Unmatched failures remain `UNKNOWN`.

The fake LLM derives its response only from structured exception metadata in
the delimited message data; payload text is not used as instructions. The
`local` profile uses Ollama for manual tool-calling checks and is not part of
the deterministic eval command.

For the local model check, start PostgreSQL and Ollama with
`docker compose -f infra/local/docker-compose.yml up -d postgres ollama`, pull
the configured `qwen3:4b` model into Ollama, then run
`./gradlew evalRun -Peval.llm=local`. In PowerShell, use
`.\gradlew.bat evalRun '-Peval.llm=local'`. That mode evaluates two cases per
failure class, persists only synthetic cases and audit records in the local
database, and writes `reports/investigator-local.json`. The local report is not
deterministic and should not replace the fake-LLM CI report.

The current manual run scored 40% accuracy against the baseline's 70% and made
no tool calls. The model remains provisional and must not be relied on for
diagnoses; see `docs/adr/003-investigator-ollama-model.md`. Invalid output and
unsafe actions are rejected and escalated.

To generate new examples, start local Ollama and run
`python evals/scripts/generate_dataset.py`. Generated examples are unreviewed;
review them before marking `reviewed` true. Add manually authored cases with
`generator: "human"` and never include real payloads.
