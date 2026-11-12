-- Read-only precondition: validate test account balances against ledger.
-- Replace the two UUID values. Both result rows must have balance_match=true.
SELECT a.id, a.balance,
       coalesce(sum(CASE WHEN le.entry_type='CREDIT' THEN le.amount ELSE -le.amount END),0) AS ledger_balance,
       a.balance = coalesce(sum(CASE WHEN le.entry_type='CREDIT' THEN le.amount ELSE -le.amount END),0) AS balance_match
FROM accounts a
LEFT JOIN ledger_entries le ON le.account_id=a.id AND le.currency=a.currency
WHERE a.id IN (
  '00000000-0000-0000-0000-000000000001'::uuid,
  '00000000-0000-0000-0000-000000000002'::uuid
)
GROUP BY a.id,a.balance;
