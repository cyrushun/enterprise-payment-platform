# Phase 2 API examples

## Prerequisites

Create two active MYR accounts using the Phase 1 API. New accounts intentionally start at zero. To exercise a successful transfer locally, fund the source account using the development-only SQL below. Never expose this technique or run it against production data.

```sql
-- Replace the UUID with the source account created through POST /api/v1/accounts.
UPDATE accounts SET balance = 1000.0000, updated_at = CURRENT_TIMESTAMP
WHERE id = '<source-account-uuid>' AND status = 'ACTIVE';
```

## Create transfer

```http
POST http://localhost:8080/api/v1/transfers
Content-Type: application/json
Idempotency-Key: local-transfer-001
```

```json
{
  "sourceAccountId": "<source-account-uuid>",
  "destinationAccountId": "<destination-account-uuid>",
  "amount": 125.5000,
  "currency": "MYR",
  "description": "Local integration test"
}
```

## Retrieve transfer

```http
GET http://localhost:8080/api/v1/transfers/<transfer-uuid>
```

## Retrieve by reference

```http
GET http://localhost:8080/api/v1/transfers/<reference>
```

## Account history

```http
GET http://localhost:8080/api/v1/accounts/<account-uuid>/transfers?page=0&size=20
```

## Verify balances and ledger

```sql
SELECT id, account_number, balance, currency FROM accounts
WHERE id IN ('<source-account-uuid>', '<destination-account-uuid>');

SELECT lt.id AS journal_id, lt.transfer_id, le.account_id, le.entry_type,
       le.amount, le.currency
FROM ledger_transactions lt
JOIN ledger_entries le ON le.ledger_transaction_id = lt.id
WHERE lt.transaction_type = 'TRANSFER'
ORDER BY lt.created_at DESC, le.entry_type;

SELECT lt.id, SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END) AS debits,
       SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END) AS credits
FROM ledger_transactions lt
JOIN ledger_entries le ON le.ledger_transaction_id = lt.id
GROUP BY lt.id
HAVING SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END)
    <> SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END);
```

The last query should return no rows for balanced journals.
