# Phase 2 — Transfers, Ledger Posting, Concurrency and Idempotency

## Delivered scope

- Synchronous account-to-account transfers through `POST /api/v1/transfers`.
- PostgreSQL pessimistic row locks acquired in deterministic UUID order.
- Source and destination status/currency validation and overdraft prevention.
- Atomic account balance updates, transfer persistence and double-entry ledger posting.
- Existing `JournalValidator` reused before ledger persistence.
- Persisted idempotency records, request SHA-256 fingerprint and transaction-scoped PostgreSQL advisory lock for concurrent duplicate keys.
- Transfer lookup by ID/reference and pageable account transfer history.

## APIs

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/transfers` | Execute transfer; requires `Idempotency-Key` |
| GET | `/api/v1/transfers/{id}` | Retrieve transfer |
| GET | `/api/v1/transfers/reference/{reference}` | Retrieve by reference |
| GET | `/api/v1/accounts/{accountId}/transfers` | Pageable incoming/outgoing history |

### Create transfer

```http
POST /api/v1/transfers
Idempotency-Key: transfer-demo-001
Content-Type: application/json
```

```json
{
  "sourceAccountId": "<source-account-uuid>",
  "destinationAccountId": "<destination-account-uuid>",
  "amount": 125.5000,
  "currency": "MYR",
  "description": "Invoice payment"
}
```

The endpoint returns HTTP 201 for both the first successful request and a replay of the same key/payload. Reusing a key with a different payload returns HTTP 409. Insufficient funds returns HTTP 422. A business-rejected transfer is not persisted as a transfer or idempotency result in this phase.

## Transaction and locking model

1. Validate request and calculate a canonical SHA-256 payload hash.
2. Acquire `pg_advisory_xact_lock(hashtextextended(scope + key, 0))` to serialize concurrent requests using the same idempotency key.
3. Return the original transfer for an identical completed key; reject a different payload.
4. Lock both account rows with `PESSIMISTIC_WRITE`, ordered by UUID.
5. Validate account status, currency and available balance after locks are held.
6. Persist transfer, mutate both account balance projections, persist one POSTED ledger transaction and its DEBIT/CREDIT entries, then persist the idempotency result.
7. Commit all changes together. Any runtime exception rolls the transaction back.

The advisory lock is PostgreSQL-specific and intentionally chosen because it serializes same-key requests before the idempotency row exists. The unique `(operation_scope, idempotency_key)` constraint remains the durable final guard. Hash collisions can only cause unnecessary serialization, not incorrect execution.

## Accounting convention in this simulation

For a customer-to-customer transfer, the source account receives a DEBIT entry and the destination receives a CREDIT entry. The existing `JournalValidator` enforces positive entries, a single currency and equal debit/credit totals. The journal and account balance changes share the same database transaction.

## Important limitations / next work

- This is an educational simulation, not a production banking ledger. Production systems should make the ledger authoritative and derive balances from posted entries or a rigorously reconciled projection.
- This phase persists successful idempotency outcomes only; rejected attempts are not retained. Add an independent audit/security policy before production.
- Idempotency scope is currently the fixed operation `TRANSFER_CREATE`; after authentication is implemented, scope it by authenticated client/customer identity.
- The API currently has no caller authorization; do not expose it to untrusted networks.
- `NUMERIC(19,4)` is retained from V1. A production currency/scale policy must be explicit per currency.
- The reference number is unique but intentionally opaque/non-sequential.
- No Kafka publication is performed here. Phase 3 should write an outbox event in this same transaction before any asynchronous publishing.

## Verification

```powershell
mvn clean test
mvn spring-boot:run
```

PostgreSQL 17 must be running. Flyway applies V2 automatically; V1 is not modified.
