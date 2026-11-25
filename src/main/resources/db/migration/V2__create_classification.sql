CREATE TABLE classification (
    message_id UUID PRIMARY KEY REFERENCES dlq_message(id),
    class VARCHAR(64) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    evidence JSONB NOT NULL,
    classifier_version VARCHAR(64) NOT NULL,
    classified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_classification_confidence CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT chk_classification_class CHECK (
        class IN (
            'TRANSIENT_INFRASTRUCTURE',
            'SCHEMA_MISMATCH',
            'VALIDATION_ERROR',
            'PERMANENT_BUSINESS_ERROR',
            'UNKNOWN'
        )
    )
);
