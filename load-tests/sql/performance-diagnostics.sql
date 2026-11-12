-- Phase 5.2.4 PostgreSQL diagnostics.
-- Run using psql against the local/test database. Replace the UUID placeholder below.
-- EXPLAIN ANALYZE executes the SELECT; use only read-only SELECT statements here.

-- 1. Account balance lookup (used by GET /accounts/{id}/balance).
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT id, customer_id, balance, currency, status, version
FROM accounts
WHERE id = '00000000-0000-0000-0000-000000000000'::uuid;

-- 2. Transfer history (both source and destination account paths).
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT *
FROM transfers
WHERE from_account_id = '00000000-0000-0000-0000-000000000000'::uuid
   OR to_account_id = '00000000-0000-0000-0000-000000000000'::uuid
ORDER BY created_at DESC
LIMIT 20;

-- 3. Recent ledger activity for one account.
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT id, ledger_transaction_id, entry_type, amount, currency, created_at
FROM ledger_entries
WHERE account_id = '00000000-0000-0000-0000-000000000000'::uuid
ORDER BY created_at DESC
LIMIT 50;

-- 4. Ready outbox claim shape. This is a SELECT-only plan; it does not claim rows.
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT id, status, available_at, created_at
FROM outbox_events
WHERE ((status IN ('PENDING','RETRY') AND available_at <= CURRENT_TIMESTAMP)
    OR (status='PROCESSING' AND locked_until < CURRENT_TIMESTAMP))
ORDER BY created_at
LIMIT 25;

-- 5. Index sizes and usage.
SELECT schemaname, relname AS table_name, indexrelname AS index_name,
       idx_scan, idx_tup_read, idx_tup_fetch,
       pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
ORDER BY idx_scan ASC, pg_relation_size(indexrelid) DESC;

-- 6. Table sizes and estimated row counts.
SELECT relname AS table_name, n_live_tup, n_dead_tup,
       pg_size_pretty(pg_total_relation_size(relid)) AS total_size
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(relid) DESC;

-- 7. Active sessions and wait states.
SELECT pid, usename, application_name, state, wait_event_type, wait_event,
       now() - query_start AS query_age, left(query, 180) AS query
FROM pg_stat_activity
WHERE datname = current_database() AND pid <> pg_backend_pid()
ORDER BY query_start NULLS LAST;

-- 8. Lock waits.
SELECT blocked.pid AS blocked_pid, blocked.wait_event_type, blocked.wait_event,
       now() - blocked.query_start AS blocked_for,
       left(blocked.query, 160) AS blocked_query,
       blocker.pid AS blocker_pid, left(blocker.query, 160) AS blocker_query
FROM pg_stat_activity blocked
JOIN pg_stat_activity blocker
  ON blocker.pid = ANY(pg_blocking_pids(blocked.pid))
ORDER BY blocked.query_start;

-- Optional slow query statistics require pg_stat_statements to be installed/enabled.
-- If the extension is available:
-- SELECT calls, mean_exec_time, max_exec_time, rows, left(query, 200) AS query
-- FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 20;
