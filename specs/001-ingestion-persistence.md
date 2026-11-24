# Spec 001 — DLQ ingestion and persistence
Status: Draft | Depends on: none

## Context
Foundation of the system: consume messages from the Kafka DLQ, extract the
failure metadata (original topic, error, stack trace, attempts), and persist
each message exactly once even under redelivery.

## User story
As an on-call developer, I want every DLQ message recorded durably and
without duplicates, so triage starts from a reliable state.

## Acceptance criteria
- [ ] Flyway V1 creates `dlq_message`: id, message_key, source_topic,
  partition, offset, payload, headers (jsonb), error_class,
  error_message, stack_trace, received_at, status.
- [ ] A consumed message is persisted with status `RECEIVED`.
- [ ] Redelivery of the same message (same topic/partition/offset) does not
  create a second row (unique constraint + idempotent handling).
- [ ] A message with missing error headers is persisted with
  `error_class = 'UNKNOWN'` without crashing the consumer.
- [ ] Invalid/binary payloads are persisted as text/base64 and flagged.
- [ ] The offset is committed only after the database commit.
- [ ] A database failure loses no message (retry with backoff; offset does
  not advance).
- [ ] The domain imports no Spring/Jackson/JDBC (ArchUnit passes).

## Out of scope
Classification, agent, reprocessing, SQS.

## Technical notes and dependencies
- Ports: `DlqSource`, `MessageRepository`. Kafka adapter in `adapter/in/kafka`.
- Idempotency key: (source_topic, partition, offset) of the DLQ record.
- Docker Compose: Kafka (KRaft) and PostgreSQL.
- ASSUMPTION: error headers follow Spring Kafka's
  `DeadLetterPublishingRecoverer` convention.

## Test plan
- Unit: header parsing, domain mapping.
- Integration (Testcontainers Kafka + Postgres): ingestion, duplicates,
  malformed payload, simulated DB failure.
- Manual: publish 3 messages with `kafka-console-producer`, check the table.

## DoD
All criteria ticked; `./gradlew build integrationTest` green; lint clean;
README explains how to start the local environment.