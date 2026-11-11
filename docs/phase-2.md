# Phase 2 — Transfers, Ledger Posting, Concurrency and Idempotency

## 2.1 Objective

Implement synchronous account-to-account transfers with transactional balance updates, persisted double-entry ledger journals, deterministic account locking and request idempotency.

## 2.2 Financial and concurrency invariants

- Both account rows are locked with pessimistic write locks in deterministic UUID order.
- Source and destination accounts must be ACTIVE and use the requested currency.
- The source account must have sufficient available balance.
- Account balance changes, transfer persistence, ledger journal/entries and the successful idempotency result commit in one PostgreSQL transaction.
- The source account receives a DEBIT entry and the destination receives a CREDIT entry in this simulation.
- Each journal must balance: total DEBIT equals total CREDIT.
- `Idempotency-Key` is mandatory for transfer creation. The same key and same payload return the original transfer; the same key with a different payload returns a conflict.
- PostgreSQL advisory transaction locking serializes concurrent requests using the same idempotency scope/key before the idempotency row exists. The unique constraint remains the durable final guard.

## 2.3 API contract

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/transfers` | Execute a transfer; requires `Idempotency-Key` |
| GET | `/api/v1/transfers/{id}` | Retrieve transfer by ID |
| GET | `/api/v1/transfers/reference/{reference}` | Retrieve transfer by reference |
| GET | `/api/v1/accounts/{accountId}/transfers` | Retrieve pageable incoming/outgoing history |

See `docs/phase-2-api-examples.md` for request examples and SQL.

## 2.4 Test preparation

Start PostgreSQL and run the application:

```powershell
docker compose up -d postgres
mvn clean test
mvn spring-boot:run
```

Create two ACTIVE MYR accounts through the Phase 1 API. New accounts deliberately start at zero. For local integration testing only, fund the source account using a disposable database fixture:

```sql
UPDATE accounts
SET balance = 1000.0000, updated_at = CURRENT_TIMESTAMP
WHERE id = '<source-account-uuid>' AND status = 'ACTIVE';
```

Do not use direct balance updates as a production funding mechanism. This SQL is only a test fixture because the project does not implement deposits in this phase.

## 2.5 Scenario A — Successful transfer

1. Submit `POST /api/v1/transfers` with a unique `Idempotency-Key`, valid source/destination account UUIDs, amount `125.5000`, currency `MYR` and a description.
2. Expected: HTTP `201 Created`; response identifies the transfer and its amount, currency and status.
3. Retrieve the transfer by ID.
4. Expected: HTTP `200 OK`; persisted transfer matches the create response.
5. Retrieve it by reference.
6. Expected: HTTP `200 OK`; same transfer is returned.
7. Retrieve transfer history for both accounts.
8. Expected: the source and destination histories include the transfer according to direction.

For a source starting at MYR 1,000.0000 and destination at MYR 0.0000, the expected post-transfer projections are MYR 874.5000 and MYR 125.5000.

## 2.6 Scenario B — Idempotency

1. Repeat the exact same request with the same `Idempotency-Key` and identical payload.
2. Expected: HTTP `201 Created` and the original transfer result; no second transfer or journal is created.
3. Reuse the same key with a different amount, account or currency.
4. Expected: HTTP `409 Conflict` with the idempotency conflict error code implemented by the service.
5. Send a request without `Idempotency-Key`.
6. Expected: request rejected as a client error; no transfer, balance mutation or ledger journal is created.
7. In an integration/concurrency test, submit concurrent identical requests with the same key and payload.
8. Expected: one logical transfer and one journal; all successful callers observe the same transfer result.

## 2.7 Scenario C — Business rejection and validation

Test insufficient funds, same source/destination account, inactive account, currency mismatch, zero/negative amount, excessive precision and unknown account IDs.

Expected behavior:

- Insufficient funds: HTTP `422 Unprocessable Entity`.
- Invalid request fields: HTTP `400 Bad Request`.
- Unknown account: HTTP `404 Not Found`.
- Conflicting idempotency payload: HTTP `409 Conflict`.

For rejected business requests, verify no partial balance change, transfer, ledger posting or successful idempotency result is committed.

## 2.8 Database verification

Check account balances:

```sql
SELECT id, account_number, balance, currency, status
FROM accounts
WHERE id IN ('<source-account-uuid>', '<destination-account-uuid>');
```

Inspect the transfer and journal entries:

```sql
SELECT t.id AS transfer_id, t.reference, t.source_account_id,
       t.destination_account_id, t.amount, t.currency, t.status,
       lt.id AS journal_id, lt.transaction_type, lt.status AS journal_status,
       le.account_id, le.entry_type, le.amount AS entry_amount, le.currency AS entry_currency
FROM transfers t
JOIN ledger_transactions lt ON lt.transfer_id = t.id
JOIN ledger_entries le ON le.ledger_transaction_id = lt.id
WHERE t.id = '<transfer-uuid>'
ORDER BY le.entry_type;
```

Find unbalanced journals:

```sql
SELECT lt.id AS journal_id,
       SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END) AS total_debits,
       SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END) AS total_credits
FROM ledger_transactions lt
JOIN ledger_entries le ON le.ledger_transaction_id = lt.id
GROUP BY lt.id
HAVING SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END)
    <> SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END);
```

Expected: no rows for balanced journals.

Inspect idempotency records using the actual table and column names in the V2 migration:

```sql
SELECT *
FROM idempotency_records
ORDER BY created_at DESC
LIMIT 20;
```

If the schema names differ in the checked-out migration, use the V2-defined names; do not create a replacement table to make the query work.

## 2.9 Regression verification

```powershell
mvn clean test
```

Run the Phase 1 Postman collection again. Customer/account creation and balance retrieval must remain unchanged.

## 2.10 Pass condition

A successful transfer changes both account projections and creates one balanced journal atomically. Replays with an identical key/payload do not duplicate financial effects; conflicting payloads and invalid/business-rejected requests do not partially mutate financial state.
