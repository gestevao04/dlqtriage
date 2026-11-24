# Tasks (living checklist)
Convention: `[ ] Spec NNN — description`. Detail lives in the spec.

## Foundation (pre-slice)
- [ ] T-0.1 Bootstrap with latest compatible stable releases; versions only in `gradle/libs.versions.toml`
- [ ] T-0.2 Gradle skeleton (Kotlin DSL, single module), `integrationTest` and `evalRun` tasks
- [ ] T-0.3 Configure ktlint, detekt, ArchUnit (dependency rule)
- [ ] T-0.4 `infra/local/docker-compose.yml` (Kafka, Postgres, Jaeger, Ollama)
- [ ] T-0.5 LICENSE and README skeleton
- [ ] T-0.6 Approve specs 001 to 006 (status -> Approved)

## Spec 001 — Ingestion and persistence
- [x] Spec 001 — migration V1 `dlq_message`
- [x] Spec 001 — domain model and `MessageRepository` port
- [x] Spec 001 — Kafka adapter (consumer, commit after persist)
- [x] Spec 001 — idempotency by topic/partition/offset
- [x] Spec 001 — handle missing headers and invalid payloads
- [x] Spec 001 — write tests (unit + integration)
- [ ] Spec 001 — review acceptance criteria
- [ ] Spec 001 — run lint and `build integrationTest`

## Spec 002 — Baseline classification and evals
- [ ] Spec 002 — taxonomy and expected actions in the domain
- [ ] Spec 002 — `RuleBasedClassifier` + `classification` persistence
- [ ] Spec 002 — sandbox failure generator
- [ ] Spec 002 — dataset generation scripts (multiple LLMs) and provenance fields
- [ ] Spec 002 — dataset >= 100 cases, human review of >= 20%, held-out split
- [ ] Spec 002 — eval harness and report
- [ ] Spec 002 — write tests
- [ ] Spec 002 — publish baseline report
- [ ] Spec 002 — review acceptance criteria
- [ ] Spec 002 — run lint and tests

## Spec 003 — Investigator agent
- [ ] Spec 003 — choose local model and validate tool calling (ADR)
- [ ] Spec 003 — `LlmPort` + adapters (Ollama, fake)
- [ ] Spec 003 — read-only tools
- [ ] Spec 003 — structured output validator
- [ ] Spec 003 — versioned prompt + payload isolation
- [ ] Spec 003 — `agent_run` / `agent_step` persistence
- [ ] Spec 003 — plug into harness and compare with baseline
- [ ] Spec 003 — adversarial cases (prompt injection)
- [ ] Spec 003 — write tests
- [ ] Spec 003 — review acceptance criteria
- [ ] Spec 003 — run lint and tests

## Spec 004 — Guardrails and actions
- [ ] Spec 004 — migration V3 `decision` / `approval`
- [ ] Spec 004 — `GuardrailPolicy` + decision table
- [ ] Spec 004 — configurable step and cost limits
- [ ] Spec 004 — idempotent `ReprocessPort` + `ExecuteActionUseCase`
- [ ] Spec 004 — approval flow (REST + token)
- [ ] Spec 004 — decision audit
- [ ] Spec 004 — measure dangerous-action rate in the harness
- [ ] Spec 004 — write tests (including property test)
- [ ] Spec 004 — review acceptance criteria
- [ ] Spec 004 — run lint and tests

## Spec 005 — Observability
- [ ] Spec 005 — tracing per message/step/tool
- [ ] Spec 005 — Micrometer metrics
- [ ] Spec 005 — structured logs and masking
- [ ] Spec 005 — `agent_run` replay
- [ ] Spec 005 — health checks
- [ ] Spec 005 — write tests
- [ ] Spec 005 — review acceptance criteria
- [ ] Spec 005 — run lint and tests

## Spec 006 — MCP server
- [ ] Spec 006 — decide transport and authentication (ADR)
- [ ] Spec 006 — read tools
- [ ] Spec 006 — `approve_action` / `reject_action` tools
- [ ] Spec 006 — audit of MCP calls
- [ ] Spec 006 — contract test with an MCP client
- [ ] Spec 006 — document client connection
- [ ] Spec 006 — write `docs/DEMO.md` and record the end-to-end demo
- [ ] Spec 006 — review acceptance criteria
- [ ] Spec 006 — run lint and tests

## MVP wrap-up
- [ ] Minimal GitHub Actions CI (`build integrationTest` + `evalRun` with fake LLM), build badge in README
- [ ] Enable Dependabot (`.github/dependabot.yml`): Gradle ecosystem, weekly schedule, minor/patch updates grouped; confirm it picks up `libs.versions.toml`
- [ ] Final eval report (baseline vs. agent) in README
- [ ] Technical post with 3 real failures found and how they were fixed