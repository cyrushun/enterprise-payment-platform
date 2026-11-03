# ADR-0006: PostgreSQL locking and persisted idempotency for transfers

- Status: Accepted for Phase 2
- Date: 2026-11-02

## Context

Transfers must prevent concurrent overdrafts and duplicate execution. The database is the system of record, and the platform currently has no authenticated client identity or asynchronous transfer workflow.

## Decisions

1. Acquire a PostgreSQL transaction-scoped advisory lock for the `TRANSFER_CREATE` operation scope and idempotency key before checking the idempotency table. This serializes concurrent first-use requests with the same key.
2. Persist a SHA-256 fingerprint of the canonical request and the completed transfer resource ID. Reusing a key with a different fingerprint is a conflict.
3. Lock both account rows with JPA `PESSIMISTIC_WRITE`, ordered by UUID, before checking balances or mutating accounts.
4. Persist the transfer, account balance projection changes, POSTED ledger transaction, two ledger entries, and idempotency result in one database transaction.
5. Retain the unique `(operation_scope, idempotency_key)` constraint as the durable uniqueness guard.

## Consequences

- Correctness is tied to PostgreSQL and the transaction boundary; Redis is not used for financial coordination.
- Same-key requests serialize. Unrelated keys are not intentionally serialized, apart from the negligible possibility of a 64-bit advisory-lock hash collision.
- Opposite-direction transfers acquire account locks in stable order, reducing deadlock risk.
- A database transaction rollback undoes the transfer, balance changes, journal entries and idempotency result together.
- Idempotency scope is a fixed operation name until authentication is introduced; a future security phase must scope it to the authenticated client/customer.
- Failed business attempts are not stored as idempotency results in this milestone.
