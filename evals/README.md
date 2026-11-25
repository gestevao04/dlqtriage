# Baseline classification evaluations

The dataset is synthetic JSONL in `dataset/baseline.jsonl`. Each record
contains the observed exception metadata, expected class and action, source
model (or `human` for hand-written cases), review status, and a `train` or
`held-out` split. The held-out split must not be used to tune rules or prompts.

Run `./gradlew evalRun` to evaluate the deterministic `RuleBasedClassifier`.
It has no network or LLM requirement and writes overall, per-class, confusion
matrix, per-split results, and misclassified case details for manual review to
`reports/baseline.json`. The report includes case IDs, expected and predicted
classes, and matching evidence without copying payloads.

The baseline taxonomy maps `TRANSIENT_INFRASTRUCTURE` to `REPROCESS`.
`SCHEMA_MISMATCH`, `VALIDATION_ERROR`, `PERMANENT_BUSINESS_ERROR`, and
`UNKNOWN` map to `ESCALATE`. Unmatched failures remain `UNKNOWN`.

To generate new examples, start local Ollama and run
`python evals/scripts/generate_dataset.py`. Generated examples are unreviewed;
review them before marking `reviewed` true. Add manually authored cases with
`generator: "human"` and never include real payloads.
