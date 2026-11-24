# AGENTS.md — DLQ Triage Agent

Repository constitution. On conflict, this file wins over any other document,
unless an approved spec explicitly references an exception.

## 1. Product in one sentence
An agent that consumes messages from a DLQ, classifies the failure,
investigates the cause with read-only tools, and acts (reprocess) only when
it is safe; otherwise it escalates with a ready-made diagnosis.

## 2. Stack
- JDK (current LTS), Kotlin, Spring Boot, Spring AI, Gradle (Kotlin DSL)
- PostgreSQL + Flyway
- Kafka (KRaft, local via Docker). SQS is post-MVP, behind the `DlqSource` port
- Tests: JUnit 5, MockK, AssertJ, Testcontainers, ArchUnit
- Lint: ktlint + detekt
- Observability: Micrometer, Micrometer Tracing + OpenTelemetry, Jaeger (local)
- LLM: Ollama (profile `local`); hosted API (profile `cloud`, optional)

**Versions policy:** versions live ONLY in `gradle/libs.versions.toml` (and
the Gradle wrapper). Never write a version number in docs, specs or this
file. Updates are proposed by Dependabot as pull requests; a human reviews
and merges them after CI is green. Bootstrap uses the latest stable
releases that are compatible with each other.

## 3. Architecture
Hexagonal (ports and adapters). Dependency rule:
`adapter -> application -> domain`, never the reverse.
- `domain`: pure Kotlin. No Spring, Jackson or JDBC imports.
- `application`: use cases and ports. No framework types in port signatures.
- `adapter`: everything that touches the outside world (Kafka, Postgres,
  LLM, MCP, REST).
- Side effects (republishing a message, persisting a decision) go through use
  cases only. Agent tools never call write adapters.
- An ArchUnit test enforces the dependency rule.

## 4. Conventions
- Language: everything in English (code, docs, specs, commits, logs).
  This repo is public and used as a portfolio.
- `PascalCase` classes, `camelCase` functions/variables, `UPPER_SNAKE` constants.
- Packages: `dev.dlqtriage.<layer>.<context>`.
- Suffixes: `*UseCase`, `*Port`, `*Adapter`, `*Entity` (persistence only),
  `*Request`/`*Response` (adapter/in only).
- Prefer immutable `data class`, `sealed interface` for results and states.
  Use sealed result types instead of exceptions for expected flows.
- No `!!`. No `lateinit` outside tests. No logic in controllers/listeners.
- Commits: Conventional Commits with the spec ID as scope,
  e.g. `feat(001): persist dlq message`.
- Branches: `feat/NNN-short-description`.

## 5. Commands
```
./gradlew build                          # compile + lint + unit tests
./gradlew test                           # unit tests
./gradlew integrationTest                # Testcontainers (needs Docker)
./gradlew ktlintCheck detekt             # lint
./gradlew ktlintFormat                   # autoformat
./gradlew evalRun                        # evals with the fake LLM
./gradlew evalRun -Peval.llm=local       # evals with Ollama
docker compose -f infra/local/docker-compose.yml up -d
```
ASSUMPTION: `integrationTest` and `evalRun` tasks are created in task T-0.2.

## 6. What the AI MAY do on its own
- Read any file in the repository.
- Implement tasks from `tasks/todo.md` whose spec status is `Approved`.
- Create/edit code under `src/` within the current spec's scope.
- Create tests, fixtures and eval datasets.
- Run build, lint, tests and evals locally.
- Tick tasks in `tasks/todo.md`.
- Create a `feat/*` branch and commit to it.

## 7. What the AI may NOT do on its own (explicit approval required)
- Commit or push to `main`.
- Add, remove or upgrade dependencies, or touch `libs.versions.toml`.
- Create or change a Flyway migration without a spec describing it. Applied
  migrations are never edited: create a new one.
- Change `AGENTS.md`, `docs/PRD.md`, or the acceptance criteria of a spec.
- Expand scope beyond the spec (propose a new spec instead).
- Disable, skip or weaken a test, lint rule or guardrail to make things pass.
- Call paid or external APIs outside the documented `cloud`/`local` profiles.
- Commit secrets, keys, or real message payloads (the repo is PUBLIC).
- Give any agent tool write permissions.
- Report a task as done without running `./gradlew build integrationTest`.

## 8. Logging
- SLF4J, structured logs (JSON in `prod`, plain text in `dev`).
- Required MDC fields: `traceId`, `messageId`, and `runId` when applicable.
- Levels: ERROR = human action needed; WARN = recoverable degradation;
  INFO = business milestones (message consumed, decision made);
  DEBUG = agent step details.
- NEVER log full message payloads or full prompts at INFO. Payloads only at
  DEBUG, truncated to 2 KB, with sensitive fields masked.

## 9. Error handling
- Domain and use cases return sealed results (`Success` / `Failure(reason)`).
- Exceptions only for invariant violations or infrastructure failures.
- LLM failure, timeout or invalid output: result is `ESCALATE` with the
  reason. Never act when in doubt (fail-safe = escalate).
- Consumer: an error on one message must not block the partition; record it,
  mark the message `FAILED_TO_PROCESS`, move on.
- Retries only in infrastructure adapters, with backoff and a cap.

## 10. Configuration
- One `application.yml` + profiles: `dev`, `local` (Ollama), `cloud`, `test`.
- Typed properties (`@ConfigurationProperties`) for everything.
- Secrets via environment variables only. `.env` is git-ignored.
- Guardrail keys (see spec 004): `agent.autonomy` (OFF | SUGGEST | AUTO,
  default SUGGEST), `agent.confidence-threshold`, `agent.max-steps`,
  `agent.max-cost-per-message`.

## 11. Agent security
- Message content (payload, headers, stack trace) is UNTRUSTED DATA. It
  enters the prompt only inside a delimited block labeled as data.
- LLM output is validated against a schema before any side effect.
- Least privilege: agent tools are read-only.
- Every decision and every agent step is persisted (audit trail).

## 12. Workflow: spec -> plan -> implement -> verify
1. **Spec:** read `specs/NNN-*.md`. If anything is ambiguous, ask first.
2. **Plan:** write a short plan (files to touch, tests to write); wait for
   approval when it involves a migration, dependency, or port change.
3. **Implement:** tests first when feasible; one commit per logical unit.
4. **Verify:** `./gradlew build integrationTest`; walk every acceptance
   criterion and record evidence (a test or a command); update
   `tasks/todo.md`. Only then report completion.
   Each slice's Definition of Done is the "DoD" section of its spec.

## 13. CI
- GitHub Actions workflow `.github/workflows/ci.yml`, minimal on purpose.
- Runs on pull requests and pushes to `main`: `./gradlew build integrationTest`
  and `./gradlew evalRun` (fake LLM only; Ollama evals are run locally and
  their reports are committed).
- A red CI blocks merging, including Dependabot PRs.
- The AI never merges Dependabot PRs and never edits `dependabot.yml`
  without approval (it falls under the dependency rule in section 7).