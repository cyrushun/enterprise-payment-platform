-- Additive observability metadata for async outbox-to-Kafka trace/correlation propagation.
-- Existing financial records and event JSON payload contracts remain unchanged.
ALTER TABLE outbox_events
    ADD COLUMN correlation_id VARCHAR(128),
    ADD COLUMN traceparent VARCHAR(55),
    ADD COLUMN tracestate VARCHAR(512);

CREATE INDEX idx_outbox_events_correlation_id
    ON outbox_events (correlation_id)
    WHERE correlation_id IS NOT NULL;
