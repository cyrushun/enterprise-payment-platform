-- Phase 3: evolve the transactional outbox created by V1 and add
-- idempotent notification-consumer tables.
-- V1 is intentionally immutable: outbox_events already exists there.

ALTER TABLE outbox_events
    ADD COLUMN topic VARCHAR(200) NOT NULL DEFAULT 'payment.transfer-events',
    ADD COLUMN event_key VARCHAR(200) NOT NULL DEFAULT '',
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD COLUMN created_at TIMESTAMPTZ;

-- Preserve the semantics and timestamps of any rows created before Phase 3.
UPDATE outbox_events
SET created_at = occurred_at,
    available_at = next_attempt_at,
    event_key = aggregate_id::text,
    status = CASE
        WHEN published_at IS NOT NULL THEN 'PUBLISHED'
        ELSE 'PENDING'
    END;

ALTER TABLE outbox_events
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN last_error TYPE VARCHAR(2000);

ALTER TABLE outbox_events
    ADD CONSTRAINT ck_outbox_status
        CHECK (status IN ('PENDING','PROCESSING','RETRY','PUBLISHED','DEAD'));

CREATE INDEX ix_outbox_ready
    ON outbox_events(status, available_at, created_at);

CREATE INDEX ix_outbox_aggregate
    ON outbox_events(aggregate_type, aggregate_id);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    consumer_name VARCHAR(120) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES processed_events(event_id),
    recipient_account_id UUID NOT NULL REFERENCES accounts(id),
    notification_type VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_event_recipient UNIQUE(event_id, recipient_account_id)
);

CREATE INDEX ix_notifications_recipient_created
    ON notifications(recipient_account_id, created_at DESC);
