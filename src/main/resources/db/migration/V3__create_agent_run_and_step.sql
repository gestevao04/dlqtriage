CREATE TABLE agent_run (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES dlq_message(id),
    prompt_version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    classification VARCHAR(64) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    evidence JSONB NOT NULL,
    suggested_action VARCHAR(16) NOT NULL,
    reasoning_summary TEXT NOT NULL,
    failure_reason TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    input_tokens INTEGER NOT NULL,
    output_tokens INTEGER NOT NULL,
    CONSTRAINT chk_agent_run_status CHECK (status IN ('COMPLETED', 'ESCALATED')),
    CONSTRAINT chk_agent_run_class CHECK (
        classification IN (
            'TRANSIENT_INFRASTRUCTURE',
            'SCHEMA_MISMATCH',
            'VALIDATION_ERROR',
            'PERMANENT_BUSINESS_ERROR',
            'UNKNOWN'
        )
    ),
    CONSTRAINT chk_agent_run_confidence CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT chk_agent_run_action CHECK (suggested_action IN ('REPROCESS', 'ESCALATE')),
    CONSTRAINT chk_agent_run_tokens CHECK (input_tokens >= 0 AND output_tokens >= 0)
);

CREATE INDEX idx_agent_run_message_id ON agent_run(message_id);
CREATE INDEX idx_agent_run_started_at ON agent_run(started_at);

CREATE TABLE agent_step (
    run_id UUID NOT NULL REFERENCES agent_run(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    summarized_input TEXT NOT NULL,
    tool_called VARCHAR(128),
    summarized_result TEXT NOT NULL,
    input_tokens INTEGER NOT NULL,
    output_tokens INTEGER NOT NULL,
    duration_millis BIGINT NOT NULL,
    PRIMARY KEY (run_id, sequence),
    CONSTRAINT chk_agent_step_sequence CHECK (sequence > 0),
    CONSTRAINT chk_agent_step_tokens CHECK (input_tokens >= 0 AND output_tokens >= 0),
    CONSTRAINT chk_agent_step_duration CHECK (duration_millis >= 0)
);
