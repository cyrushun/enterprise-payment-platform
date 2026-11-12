-- Additive Phase 4 operations hardening; V1-V4 remain unchanged.
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS lease_token UUID;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(120);
CREATE INDEX IF NOT EXISTS ix_outbox_status_available ON outbox_events(status, available_at, created_at);
CREATE INDEX IF NOT EXISTS ix_outbox_locked_until ON outbox_events(status, locked_until);
CREATE INDEX IF NOT EXISTS ix_reconciliation_runs_started ON reconciliation_runs(started_at DESC);
