# Phase 2 — Transfer API and Postman Testing Guide

Base URL: `http://localhost:8080`.

## Prerequisites and fixtures

1. PostgreSQL is running and Flyway has applied V2.
2. Create two ACTIVE MYR accounts through the Phase 1 APIs.
3. Record `sourceAccountId` and `destinationAccountId`.
4. For this local simulation only, fund the source account using a disposable test database:

```sql
UPDATE accounts
SET balance = 1000.0000, updated_at = CURRENT_TIMESTAMP
WHERE id = '<source-account-uuid>' AND status = 'ACTIVE';
```

Do not use direct balance mutation as a real deposit operation.

## 1. Create a transfer

```http
POST /api/v1/transfers
Content-Type: application/json
Idempotency-Key: phase2-transfer-001

{
  "sourceAccountId": "<source-account-uuid>",
  "destinationAccountId": "<destination-account-uuid>",
  "amount": 125.5000,
  "currency": "MYR",
  "description": "Phase 2 transfer test"
}
```

Expected: HTTP `201 Created`. Save the returned transfer ID and reference.

## 2. Verify transfer retrieval

```http
GET /api/v1/transfers/{{transferId}}
```

Expected: HTTP `200 OK`.

```http
GET /api/v1/transfers/reference/{{transferReference}}
```

Expected: HTTP `200 OK` and the same transfer.

## 3. Verify account history

```http
GET /api/v1/accounts/{{sourceAccountId}}/transfers?page=0&size=20
GET /api/v1/accounts/{{destinationAccountId}}/transfers?page=0&size=20
```

Expected: HTTP `200 OK`; transfer appears in the relevant account history.

## 4. Idempotency scenarios

1. Repeat the exact create request with `Idempotency-Key: phase2-transfer-001` and identical JSON.
   - Expected: HTTP `201 Created` with the original transfer result.
   - Database must still contain one logical transfer and one journal for this request.
2. Reuse the same key with a different amount or destination.
   - Expected: HTTP `409 Conflict`.
3. Omit `Idempotency-Key`.
   - Expected: client error; no financial side effect.
4. In an integration test, submit concurrent identical requests with the same key/payload.
   - Expected: one logical transfer and one journal.

## 5. Business and validation scenarios

| Scenario | Expected HTTP status |
|---|---:|
| Insufficient funds | 422 |
| Unknown source/destination account | 404 |
| Invalid amount or missing fields | 400 |
| Same source and destination account | 400 or the domain validation status implemented by the endpoint |
| Currency mismatch/inactive account | 422 |
| Same idempotency key with different payload | 409 |

After every rejected request, verify that neither account balance changed and no transfer/journal was committed.

## 6. Verify balances and ledger entries

```sql
SELECT id, account_number, balance, currency
FROM accounts
WHERE id IN ('<source-account-uuid>', '<destination-account-uuid>');

SELECT t.id AS transfer_id, t.reference, t.amount, t.currency,
       lt.id AS journal_id, lt.status AS journal_status,
       le.account_id, le.entry_type, le.amount AS entry_amount, le.currency
FROM transfers t
JOIN ledger_transactions lt ON lt.transfer_id = t.id
JOIN ledger_entries le ON le.ledger_transaction_id = lt.id
WHERE t.id = '<transfer-uuid>'
ORDER BY le.entry_type;
```

Find unbalanced journals:

```sql
SELECT lt.id AS journal_id,
       SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END) AS debits,
       SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END) AS credits
FROM ledger_transactions lt
JOIN ledger_entries le ON le.ledger_transaction_id = lt.id
GROUP BY lt.id
HAVING SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END)
    <> SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END);
```

Expected: no rows. For the sample transfer, source balance decreases by 125.5000 and destination balance increases by 125.5000.

## Pass condition

Transfer creation is atomic, the journal balances, idempotent replay does not duplicate financial effects, and invalid/rejected requests leave account balances unchanged.
