# Enterprise Payment Platform — Phase 4 Complete Testing & Verification Guide

**Scope:** Phase 4.1–4.6, with regression gates for Phases 0–3. This is an executable acceptance plan, not a claim that tests have passed. Run against a disposable local database only when injecting faults or changing ledger/outbox rows.

## 1. Environment and baseline

Target: Windows 11, JDK 21, Maven 3.9+, Docker Desktop/Compose, Postman. From the repository root (PowerShell):

```powershell
java -version
mvn -version
docker version
docker compose version
Copy-Item .env .env.backup -ErrorAction SilentlyContinue
docker compose up -d
docker compose ps
docker compose logs postgres --tail 100
docker compose logs kafka --tail 100
docker compose logs redis --tail 100
mvn clean test
mvn spring-boot:run
```

Compose service names in this baseline are `postgres`, `kafka`, `kafka-ui`, and `redis`. Confirm with `docker compose ps` before stopping anything. Do not overwrite an existing `.env` with `.env.example`. The bundled RSA key pair is local-development-only; replace it with managed key material and rotation before any deployment.

Health checks:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
Invoke-RestMethod http://localhost:8080/actuator/health/liveness
Invoke-RestMethod http://localhost:8080/actuator/metrics
```

Readiness includes `readinessState` and `db`; liveness includes only `livenessState`. Redis and Kafka are monitored separately, not used as readiness gates in this baseline. A dependency outage must not cause liveness to fail and create restart loops.

## 2. Migration and schema gate (all phases)

```sql
SELECT version, description, success
FROM flyway_schema_history ORDER BY installed_rank;

SELECT to_regclass('public.customers'), to_regclass('public.accounts'),
       to_regclass('public.ledger_transactions'), to_regclass('public.ledger_entries'),
       to_regclass('public.outbox_events'), to_regclass('public.audit_logs'),
       to_regclass('public.users'), to_regclass('public.refresh_tokens'),
       to_regclass('public.reconciliation_runs'),
       to_regclass('public.reconciliation_discrepancies');
```

Expected: every migration V1–V5 has `success=true`; every relation above resolves. V1–V5 are immutable after deployment. Any future schema change is a new forward-only migration. Verify both a clean database and an existing database already migrated to V4 receiving V5; never delete or rewrite migration history to make a test pass.

## 3. Common API error contract

All MVC/controller exceptions and Spring Security filter failures use the same response shape:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "timestamp": "2026-11-06T09:00:00Z",
  "details": ["email: must be a well-formed email address"]
}
```

| Scenario | Expected HTTP | Code |
|---|---:|---|
| Missing, malformed, expired or invalid JWT | 401 | `UNAUTHENTICATED` |
| Authenticated but role/ownership denied | 403 | `FORBIDDEN` |
| Bean/constraint validation | 400 | `VALIDATION_ERROR` |
| Malformed JSON, UUID or parameter | 400 | `MALFORMED_REQUEST` |
| Missing required header/parameter | 400 | `MISSING_REQUIRED_HEADER` / `MISSING_REQUIRED_PARAMETER` |
| Duplicate/unique constraint or idempotency conflict | 409 | `DUPLICATE_CUSTOMER_EMAIL` / `RESOURCE_CONFLICT` / `IDEMPOTENCY_CONFLICT` |
| Invalid outbox state | 409 | `INVALID_STATE` |
| Missing resource | 404 | resource-specific code |
| Insufficient balance/business invariant | 422 | domain-specific code |
| Database resource failure/connection loss, query timeout, Kafka broker failure/timeout | 503 | `DEPENDENCY_UNAVAILABLE` |
| SQL grammar/programming defect or unexpected failure | 500 | `INTERNAL_ERROR` |
| Unexpected programming failure | 500 | `INTERNAL_ERROR` |

Client responses must not contain SQL, stack traces, internal exception messages, credentials, private keys, password hashes or tokens. Server logs retain diagnostic causes. A database constraint conflict remains 409; it must not be converted into a dependency 503.

## 4. Phase-by-phase regression verification

### Phase 0 — Foundation and architecture

1. Review `pom.xml`: Java release 21, Spring Boot dependency management, Testcontainers version `1.21.4`.
2. Run `mvn clean test` and confirm the application context and Testcontainers test profile start.
3. Start Compose and confirm PostgreSQL, Kafka, Redis and Kafka UI containers are healthy.
4. Start the API and verify `/actuator/health`, readiness and liveness return HTTP 200.
5. Confirm Flyway applies V1 on a clean database and Hibernate `ddl-auto=validate` starts without schema drift.
6. Review architecture, deployment, database design, business/functional/non-functional requirements and ADRs. Confirm no secret is committed in `.env` or application logs.

**Pass:** clean startup, successful migration, dependency containers healthy, schema validation clean, tests green.

### Phase 1 — Customers and accounts

1. Import `Enterprise-Payment-Platform-Phase1.postman_collection.json` and local environment.
2. Run create customer → get customer → create account → get account → balance in collection order.
3. Confirm generated IDs are captured in collection variables and the account belongs to the created customer.
4. Try duplicate customer email; expect 409. Try invalid email/missing required fields; expect 400.
5. Verify persisted customer/account rows and account currency/status using SQL read-only queries.
6. Attempt cross-customer account access after Phase 4 security is enabled; expect 403, not data disclosure.

**Pass:** valid lifecycle succeeds, invalid/duplicate inputs return stable errors, ownership is preserved.

### Phase 2 — Transfers, idempotency and ledger

1. Import and run `Enterprise-Payment-Platform-Phase2.postman_collection.json`; set source/destination account IDs and ensure source has sufficient funds using the documented local-only seed procedure.
2. Create transfer; expect 201 and `COMPLETED`. Replay same idempotency key and same payload; expect the same transfer ID and no second journal.
3. Reuse the same idempotency key with a different payload; expect 409 `IDEMPOTENCY_CONFLICT`.
4. Submit a transfer exceeding available funds; expect 422 `INSUFFICIENT_BALANCE`.
5. Inspect journal entries. For every completed transfer, debit total equals credit total and both equal the transfer amount; account balance changes are consistent with ledger postings.
6. Re-run transfer integration tests with concurrent requests using the same key; assert one transfer and one journal are committed.

**Pass:** atomic transfer, idempotent retry, balanced journal, no overdraft, no duplicate posting.

### Phase 3 — Transactional outbox and notifications

1. Import and run `Enterprise-Payment-Platform-Phase3.postman_collection.json` after a successful Phase 2 transfer.
2. Confirm the outbox event is committed in the same database transaction as the transfer.
3. Confirm publisher sends the event and consumer creates one notification. Re-deliver the same event; processed-event idempotency prevents a duplicate notification.
4. Stop Kafka locally, create a transfer, and verify the event remains in PostgreSQL. Restore Kafka and verify eventual publication.
5. Verify retry attempts, next-attempt scheduling and DEAD transition after configured max attempts.

**Pass:** no lost event on broker outage, eventual publish, consumer deduplication and bounded retry/dead-letter behavior.

## 5. Phase 4.1 — Authentication, JWT, RBAC and ownership

Use `Enterprise-Payment-Platform-Phase4.postman_collection.json`. Registration creates CUSTOMER only.

1. Register a unique synthetic user: expect 201 and access/refresh token pair. Duplicate email → 409. Invalid email/short password → 400.
2. Login with valid credentials → 200. Wrong password and unknown email must return indistinguishable 401 responses.
3. `GET /api/v1/auth/me` with token → 200; without token, malformed token, tampered signature or expired token → 401.
4. Refresh once → new pair. Reuse old refresh token → 401 and verify family revocation/reuse policy. Logout → 204; refresh logged-out token → 401.
5. Assign OPERATIONS to a disposable local test user through SQL, login again, and use the new token. Do not expose role assignment through public registration.
6. CUSTOMER requests to audit/reconciliation/outbox/operations endpoints → 403. OPERATIONS/ADMIN requests → authorized.
7. Customer A must not read Customer B's account/transfer; expect 403 without disclosing whether the resource exists.
8. Confirm access-token TTL, issuer and audience checks. Test wrong issuer/audience and signature using test fixtures only.

Role fixture:

```sql
INSERT INTO user_roles(user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r
WHERE u.email='ops.test@example.test' AND r.name='OPERATIONS'
ON CONFLICT DO NOTHING;
```

Remove the role fixture or drop the disposable database after testing.

## 6. Phase 4.2 — Audit logging

Audit schema is `audit_logs(id, actor_id, action, resource_type, resource_id, correlation_id, details, occurred_at)`. There is no `created_at` column in this table; use `occurred_at` in queries.

1. Perform successful registration/login/refresh/logout and successful customer/account/transfer operations covered by the audit wiring. The wired action matrix is `AUTH_REGISTER`, `AUTH_LOGIN`, `AUTH_REFRESH`, `AUTH_LOGOUT`, `CUSTOMER_CREATED`, `ACCOUNT_OPENED`, and `TRANSFER_CREATED`.
2. With OPERATIONS/ADMIN token, call `GET /api/v1/audit-logs?page=0&size=25`; expect 200 and verify actor/action/resource/timestamp fields. Fetch a known detail ID; unknown ID → 404 `AUDIT_LOG_NOT_FOUND`.
3. CUSTOMER list/detail calls → 403.
4. Query persisted events:

```sql
SELECT id, actor_id, action, resource_type, resource_id,
       correlation_id, occurred_at, details
FROM audit_logs ORDER BY occurred_at DESC LIMIT 100;
```

5. Inspect `details` and confirm it contains no password, password hash, access token, refresh token, private key or other credential.
6. Exercise failed login, forbidden role access, invalid transfer and duplicate resource. Record which events are audited by current wiring. **Do not assume failed attempts are audited unless the event is explicitly wired.** If the acceptance requirement includes security-failure audit, treat missing records as a defect and add an explicit security-event policy rather than silently changing success-event semantics.

**Pass:** successful wired operations leave attributable, non-sensitive audit records; list/detail access is role-protected; audit read failures use the common API error shape.

## 7. Phase 4.3 — Reconciliation and consistency

Manual execution is synchronous and now uses `ReconciliationService.runNow()`. The scheduler is a separate trigger and logs scheduled failures; the manual endpoint must not return a false 200 after a failed scan.

1. With OPERATIONS/ADMIN token, POST `/api/v1/reconciliation/run`. On success expect 200 with `runId`, `status=COMPLETED`, `accountsChecked`, and `discrepanciesFound`.
2. A discrepancy is a successful detection result, not an HTTP failure: run remains COMPLETED and discrepancy count is positive. No balances or ledger entries are auto-repaired.
3. GET `/api/v1/reconciliation/runs`; retrieve `runId`, then GET `/api/v1/reconciliation/runs/{runId}/discrepancies`.
4. Malformed UUID → 400 `MALFORMED_REQUEST`; CUSTOMER → 403; database unavailable → 503 `DEPENDENCY_UNAVAILABLE`.
5. In a disposable database, alter a test account balance within a transaction, run reconciliation, and verify `ACCOUNT_LEDGER_BALANCE` is persisted. Roll back/restore fixture after verifying. Repeat with an intentionally unbalanced journal or transfer fixture only if the schema permits safe isolated test data.
6. To verify execution failure propagation, use a test-level fault-injection integration test that makes one reconciliation query fail after run creation. Assert API service throws `ReconciliationExecutionException`, response is 503 when caused by persistence outage (or 500 for unexpected scan defect), and run status is FAILED when the database remains available to persist it. If failure-status persistence itself fails, original exception must be retained and secondary persistence failure attached/logged.
7. Verify scheduled execution uses the same service; scheduled failures are logged and do not terminate scheduler thread. Trigger via a test scheduler or temporarily shorten cron only in local test profile.

**Pass:** clean and discrepancy-bearing runs complete with durable findings; execution failures never masquerade as successful runs; no automatic repair.

## 8. Phase 4.4 — Database, Kafka and Redis resilience

1. Review `application.yml`: Hikari max pool 10/min idle 2, configurable connection timeout (default 5s), validation timeout 3s; Redis connect/command timeout defaults 2s; Kafka producer `acks=all`, idempotence, retries and bounded delivery/request timeouts.
2. Stop Redis (`docker compose stop redis`). Verify Redis-dependent functionality fails within configured bounds and is reported safely; unrelated account/ledger API paths should remain usable if they do not require Redis. Restart: `docker compose up -d redis`.
3. Stop Kafka (`docker compose stop kafka`). With PostgreSQL healthy, complete a transfer and verify its outbox event persists. Publisher retries; it must not mark event published. Restart Kafka and verify eventual publish. `docker compose up -d kafka`.
4. Stop PostgreSQL (`docker compose stop postgres`) only in disposable local environment. Readiness becomes DOWN; database-backed requests fail within the connection timeout and return sanitized 503 if exception reaches MVC handler. Restart and verify readiness recovers.
5. Confirm liveness remains UP during external dependency outages; dependency health belongs in readiness/metrics or explicit dependency health, not liveness.
6. Confirm logs show retries/backoff and no unbounded retry loop. Confirm retry exhaustion has a bounded terminal state and operator-visible metric/log.
7. Restore all services with `docker compose up -d`; rerun readiness and smoke tests.

**Pass:** bounded timeouts/retries, no event loss, clean recovery, sanitized 503, liveness isolation.

## 9. Phase 4.5 — Outbox lease recovery, fencing, retries, DLT and metrics

1. OPERATIONS/ADMIN: list and get events. Unknown UUID → 404 `OUTBOX_EVENT_NOT_FOUND`; malformed UUID → 400.
2. Stop Kafka, create a transfer and verify outbox event remains persisted. Restore Kafka and verify retry/publish.
3. Verify claim uses bounded batch size and row locking (`SKIP LOCKED`) so multiple publisher workers do not claim the same row.
4. Exercise `OutboxLeaseFencingTest`: worker A claims, lease expires, worker B reclaims; worker A's stale lease token cannot publish/fail the row. Worker B remains authoritative.
5. Test stale lease recovery by setting `locked_until` in the past for a PROCESSING row in a disposable database. Next claim should reclaim it and issue a new lease token/owner.
6. Force repeated send failures until configured `app.outbox.max-attempts`; verify DEAD state and bounded/sanitized `last_error`. Exact state names and timing are governed by implementation/config; inspect row before/after each attempt rather than assuming fixed wall-clock delays.
7. Replay DEAD event → success and status returns to retryable state with attempts/lease/error fields reset according to entity transition. Replay non-DEAD → 409 `INVALID_STATE`. Verify unknown ID → 404.
8. DLT replay with valid payload and healthy broker → 200 with topic/partition/offset. Blank eventKey/payload → 400 `VALIDATION_ERROR`. Broker down or timeout → 503 `DEPENDENCY_UNAVAILABLE`; interrupt restores thread interrupt status.
9. Verify duplicate event delivery does not duplicate notifications.
10. Inspect `/actuator/metrics` and metric detail endpoints for registered `payment.outbox.*` instruments; use exact names returned by the endpoint rather than assuming names. Confirm counters change after success/failure and gauges/timers reflect queue/latency if instrumented.

**Pass:** exclusive claims, lease fencing, recoverable stale leases, bounded retries, controlled DEAD replay, DLT failure mapped to 503, idempotent consumer.

## 10. Phase 4.6 — Security, API error, integration and regression acceptance

1. Run `mvn clean test`; retain full logs and fail the gate on any failing test.
2. Run the Phase 4 Postman collection sequentially. The collection includes auth lifecycle, CUSTOMER denial, privileged success, reconciliation result, malformed UUID, unknown outbox, invalid state, blank DLT payload and CUSTOMER DLT denial.
3. Manually execute dependency-outage cases in Sections 7–9; Postman cannot safely orchestrate stopping Docker dependencies as part of a normal collection run.
4. Run Phase 1, 2 and 3 collections after Phase 4. Confirm existing contracts and idempotency behavior remain unchanged.
5. Verify exception mappings for 400/401/403/404/409/422/503/500; all bodies contain `code`, `message`, `timestamp`, `details`.
6. Run clean-DB and V4→V5 migration tests; verify V1–V5 files unchanged from baseline.
7. Capture evidence: commit/build version, environment, test run timestamp, Postman run summary, Flyway history, sanitized DB queries, application logs and screenshots where useful.

**Phase 4 exit criteria:** all automated tests green; all Postman positive/negative tests pass; fault-injection scenarios meet documented outcomes; migrations are intact; no secret leakage; all phase docs and README match delivered behavior.

## 11. Troubleshooting evidence bundle

For any failure provide: full Maven output, first relevant `Caused by` chain, request method/path, HTTP status and sanitized body, app logs around same timestamp, active profile, `docker compose ps`, and dependency logs. Redact bearer/refresh tokens, passwords, personal data, private keys and database credentials.
