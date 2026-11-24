# DLQ Triage

DLQ Triage consumes dead-letter Kafka records, persists them durably in PostgreSQL,
and prepares the data for later classification and investigation.

## Local environment

Start PostgreSQL and Kafka:

```powershell
docker compose -f infra/local/docker-compose.yml up -d
```

Run the application with the local profile:

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

The default DLQ topic is `dlq`; configure it with `DLQ_KAFKA_TOPIC` only when
the broker-side environment uses a different topic.

## Manual verification

Publish a message:

```powershell
docker compose -f infra/local/docker-compose.yml exec kafka \
  /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic dlq
```

Query PostgreSQL:

```powershell
docker compose -f infra/local/docker-compose.yml exec postgres \
  psql -U dlqtriage -d dlqtriage \
  -c "SELECT id, source_topic, partition, offset, payload_encoding, payload_invalid, error_class, status FROM dlq_message;"
```

## Tests

```powershell
.\gradlew.bat build
.\gradlew.bat integrationTest
```
