CREATE TABLE dlq_message (
    id UUID PRIMARY KEY,
    message_key TEXT,
    source_topic VARCHAR(255) NOT NULL,
    partition INTEGER NOT NULL,
    "offset" BIGINT NOT NULL,
    payload TEXT NOT NULL,
    payload_encoding VARCHAR(16) NOT NULL DEFAULT 'TEXT',
    payload_invalid BOOLEAN NOT NULL DEFAULT FALSE,
    headers JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_class VARCHAR(255) NOT NULL DEFAULT 'UNKNOWN',
    error_message TEXT,
    stack_trace TEXT,
    received_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT uq_dlq_message_source_partition_offset
        UNIQUE (source_topic, partition, "offset"),
    CONSTRAINT chk_dlq_message_payload_encoding
        CHECK (payload_encoding IN ('TEXT', 'BASE64')),
    CONSTRAINT chk_dlq_message_status
        CHECK (status IN ('RECEIVED', 'FAILED_TO_PROCESS'))
);

CREATE INDEX idx_dlq_message_status ON dlq_message(status);
CREATE INDEX idx_dlq_message_received_at ON dlq_message(received_at);
