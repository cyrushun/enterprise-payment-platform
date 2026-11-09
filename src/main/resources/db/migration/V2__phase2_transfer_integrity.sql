-- Phase 2: transfer reference uniqueness and explicit idempotency expiry policy.
-- Align V1 CHAR(3) columns with the existing JPA String mappings (VARCHAR(3)).
ALTER TABLE accounts ALTER COLUMN currency TYPE VARCHAR(3) USING currency::VARCHAR(3);
ALTER TABLE transfers ALTER COLUMN currency TYPE VARCHAR(3) USING currency::VARCHAR(3);
ALTER TABLE ledger_entries ALTER COLUMN currency TYPE VARCHAR(3) USING currency::VARCHAR(3);

-- Existing V1 tables already contain transfers, ledger_transactions, ledger_entries and idempotency_records.
CREATE UNIQUE INDEX IF NOT EXISTS uq_transfers_reference
    ON transfers(reference) WHERE reference IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_idempotency_scope_key_created
    ON idempotency_records(operation_scope, idempotency_key, created_at DESC);

-- The schema supports an optional expires_at value. Phase 2 does not expire keys automatically;
-- define retention only after client retry windows are agreed.
