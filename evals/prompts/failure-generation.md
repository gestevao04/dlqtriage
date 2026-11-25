# Synthetic failure generation

Run `python evals/scripts/generate_dataset.py` with a local Ollama server and
the two model names configured in the script. It calls only
`http://localhost:11434` (override with `OLLAMA_URL`) and records the model
name in each case's `generator` field. The checked-in evaluation command never
calls the generator, a model, or a network service.

The script labels the class and action from the requested class, not from
model-provided labels. Generated cases start with `reviewed: false`; a human
must inspect the case and correct any inaccurate labels or malformed examples
before setting the field to `true`. Add hand-written examples with
`generator: "human"` and apply the same review process.

Keep the held-out split separate from rule development. The harness reports
held-out metrics, but rule changes must be based only on the train split.
Never use held-out cases to tune classifier rules or prompts.
