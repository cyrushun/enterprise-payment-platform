-- Phase 0 foundational schema.
-- Monetary amounts use NUMERIC, never floating-point types.
-- All timestamps are stored as TIMESTAMPTZ and application code uses UTC Instant.

CREATE TABLE customers (
    id              UUID PRIMARY KEY,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(320) NOT NULL,
    phone_number    VARCHAR(32),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_customers_email UNIQUE (email),
    CONSTRAINT ck_customers_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE TABLE accounts (
    id                  UUID PRIMARY KEY,
    customer_id         UUID NOT NULL REFERENCES customers(id),
    account_number      VARCHAR(34) NOT NULL,
    account_type        VARCHAR(20) NOT NULL,
    currency            CHAR(3) NOT NULL,
    balance             NUMERIC(19, 4) NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    version             BIGINT NOT NULL DEFAULT 0,
    opened_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_accounts_account_number UNIQUE (account_number),
    CONSTRAINT ck_accounts_type CHECK (account_type IN ('SAVINGS', 'CURRENT')),
    CONSTRAINT ck_accounts_status CHECK (status IN ('PENDING', 'ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT ck_accounts_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_accounts_balance_nonnegative CHECK (balance >= 0),
    CONSTRAINT ck_accounts_version_nonnegative CHECK (version >= 0)
);

CREATE INDEX ix_accounts_customer_id ON accounts(customer_id);
CREATE INDEX ix_accounts_customer_status ON accounts(customer_id, status);

CREATE TABLE transfers (
    id                  UUID PRIMARY KEY,
    from_account_id     UUID NOT NULL REFERENCES accounts(id),
    to_account_id       UUID NOT NULL REFERENCES accounts(id),
    amount              NUMERIC(19, 4) NOT NULL,
    currency            CHAR(3) NOT NULL,
    status              VARCHAR(24) NOT NULL,
    reference           VARCHAR(140),
    failure_code        VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_transfers_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transfers_distinct_accounts CHECK (from_account_id <> to_account_id),
    CONSTRAINT ck_transfers_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_transfers_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED')
    )
);

CREATE INDEX ix_transfers_from_account_created ON transfers(from_account_id, created_at DESC);
CREATE INDEX ix_transfers_to_account_created ON transfers(to_account_id, created_at DESC);
CREATE INDEX ix_transfers_status_created ON transfers(status, created_at);

CREATE TABLE ledger_transactions (
    id                  UUID PRIMARY KEY,
    transfer_id         UUID REFERENCES transfers(id),
    transaction_type    VARCHAR(32) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    description         VARCHAR(255),
    effective_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ledger_transaction_type CHECK (
        transaction_type IN ('TRANSFER', 'DEPOSIT', 'WITHDRAWAL', 'REFUND', 'ADJUSTMENT')
    ),
    CONSTRAINT ck_ledger_transaction_status CHECK (status IN ('PENDING', 'POSTED', 'REVERSED'))
);

CREATE UNIQUE INDEX uq_ledger_transaction_transfer
    ON ledger_transactions(transfer_id)
    WHERE transfer_id IS NOT NULL;

CREATE TABLE ledger_entries (
    id                      UUID PRIMARY KEY,
    ledger_transaction_id   UUID NOT NULL REFERENCES ledger_transactions(id),
    account_id              UUID NOT NULL REFERENCES accounts(id),
    entry_type              VARCHAR(6) NOT NULL,
    amount                  NUMERIC(19, 4) NOT NULL,
    currency                CHAR(3) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ledger_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_entry_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_ledger_entry_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX ix_ledger_entries_account_created
    ON ledger_entries(account_id, created_at DESC);
CREATE INDEX ix_ledger_entries_transaction
    ON ledger_entries(ledger_transaction_id);

-- Idempotency is persisted and scoped to an operation/client scope.
-- request_hash detects accidental reuse of the same key for a different payload.
CREATE TABLE idempotency_records (
    id                  UUID PRIMARY KEY,
    idempotency_key     VARCHAR(128) NOT NULL,
    operation_scope     VARCHAR(100) NOT NULL,
    request_hash        VARCHAR(128) NOT NULL,
    resource_id         UUID,
    response_status     INTEGER,
    response_body       JSONB,
    status              VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at          TIMESTAMPTZ,
    CONSTRAINT uq_idempotency_scope_key UNIQUE (operation_scope, idempotency_key),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

CREATE INDEX ix_idempotency_expires_at ON idempotency_records(expires_at);

-- Transactional outbox: inserted in the same database transaction as business state.
-- A later worker publishes rows to Kafka and marks them published.
CREATE TABLE outbox_events (
    id                  UUID PRIMARY KEY,
    aggregate_type      VARCHAR(100) NOT NULL,
    aggregate_id        UUID NOT NULL,
    event_type          VARCHAR(150) NOT NULL,
    payload             JSONB NOT NULL,
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at        TIMESTAMPTZ,
    attempts            INTEGER NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error          VARCHAR(1000),
    CONSTRAINT ck_outbox_attempts_nonnegative CHECK (attempts >= 0)
);

CREATE INDEX ix_outbox_unpublished
    ON outbox_events(next_attempt_at, occurred_at)
    WHERE published_at IS NULL;

CREATE TABLE audit_logs (
    id                  UUID PRIMARY KEY,
    actor_id            UUID,
    action              VARCHAR(100) NOT NULL,
    resource_type       VARCHAR(100) NOT NULL,
    resource_id         UUID,
    correlation_id      VARCHAR(100),
    details             JSONB,
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_audit_resource_time
    ON audit_logs(resource_type, resource_id, occurred_at DESC);
CREATE INDEX ix_audit_actor_time
    ON audit_logs(actor_id, occurred_at DESC);
