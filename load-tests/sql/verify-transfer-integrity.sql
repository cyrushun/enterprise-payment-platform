-- Phase 5.2.3 post-load financial integrity checks.
-- All statements are read-only. Any returned discrepancy rows require investigation.

-- A. Transfer row has exactly one matching posted ledger transaction and two entries.
SELECT t.id AS transfer_id, t.amount,
       count(DISTINCT lt.id) AS ledger_transaction_count,
       count(le.id) AS ledger_entry_count,
       coalesce(sum(CASE WHEN le.entry_type='DEBIT' THEN le.amount ELSE 0 END),0) AS debit_total,
       coalesce(sum(CASE WHEN le.entry_type='CREDIT' THEN le.amount ELSE 0 END),0) AS credit_total
FROM transfers t
LEFT JOIN ledger_transactions lt ON lt.transfer_id=t.id
LEFT JOIN ledger_entries le ON le.ledger_transaction_id=lt.id
WHERE t.created_at >= now() - interval '24 hours'
GROUP BY t.id,t.amount
HAVING count(DISTINCT lt.id) <> 1 OR count(le.id) <> 2
    OR coalesce(sum(CASE WHEN le.entry_type='DEBIT' THEN le.amount ELSE 0 END),0) <> t.amount
    OR coalesce(sum(CASE WHEN le.entry_type='CREDIT' THEN le.amount ELSE 0 END),0) <> t.amount;

-- B. Idempotency scope/key duplicates (should return no rows).
SELECT operation_scope, idempotency_key, count(*)
FROM idempotency_records
GROUP BY operation_scope,idempotency_key
HAVING count(*) > 1;

-- C. Account balance versus ledger-derived balance (should return no rows).
SELECT a.id, a.balance,
       coalesce(sum(CASE WHEN e.entry_type='CREDIT' THEN e.amount ELSE -e.amount END),0) AS ledger_balance
FROM accounts a
LEFT JOIN ledger_entries e ON e.account_id=a.id AND e.currency=a.currency
GROUP BY a.id,a.balance
HAVING a.balance <> coalesce(sum(CASE WHEN e.entry_type='CREDIT' THEN e.amount ELSE -e.amount END),0);

-- D. Unbalanced journals (should return no rows).
SELECT lt.id,
       coalesce(sum(CASE WHEN le.entry_type='DEBIT' THEN le.amount ELSE 0 END),0) AS debit,
       coalesce(sum(CASE WHEN le.entry_type='CREDIT' THEN le.amount ELSE 0 END),0) AS credit
FROM ledger_transactions lt
LEFT JOIN ledger_entries le ON le.ledger_transaction_id=lt.id
GROUP BY lt.id
HAVING coalesce(sum(CASE WHEN le.entry_type='DEBIT' THEN le.amount ELSE 0 END),0)
    <> coalesce(sum(CASE WHEN le.entry_type='CREDIT' THEN le.amount ELSE 0 END),0);

-- E. Outbox backlog and oldest pending age.
SELECT status, count(*) AS events,
       max(extract(epoch FROM (now()-created_at)))::bigint AS max_age_seconds
FROM outbox_events
WHERE status IN ('PENDING','RETRY','PROCESSING','DEAD')
GROUP BY status
ORDER BY status;

-- F. Notification idempotency duplicates (should return no rows).
SELECT event_id, recipient_account_id, count(*)
FROM notifications
GROUP BY event_id,recipient_account_id
HAVING count(*) > 1;

-- G. Most recent Phase 5.2 idempotency run keys.
-- Copy the key printed by idempotency-concurrency.js into the LIKE expression if you want
-- to isolate one run. The unique scope/key constraint should yield exactly one row.
SELECT operation_scope, idempotency_key, count(*) AS record_count,
       count(DISTINCT resource_id) AS distinct_resource_count,
       min(resource_id) AS transfer_id
FROM idempotency_records
WHERE idempotency_key LIKE 'phase52-idem-%'
GROUP BY operation_scope, idempotency_key
ORDER BY max(created_at) DESC
LIMIT 20;
