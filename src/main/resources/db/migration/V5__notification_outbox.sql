CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    dead_lettered_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    CONSTRAINT chk_notification_outbox_attempts
        CHECK (attempts >= 0 AND max_attempts > 0 AND attempts <= max_attempts),
    CONSTRAINT chk_notification_outbox_status
        CHECK (status IN ('PENDING', 'RETRY', 'PROCESSING', 'PROCESSED', 'DEAD'))
);

CREATE INDEX idx_notification_outbox_due
    ON notification_outbox(next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'RETRY');

CREATE INDEX idx_notification_outbox_dead
    ON notification_outbox(dead_lettered_at DESC)
    WHERE status = 'DEAD';
