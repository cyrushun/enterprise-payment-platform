# Phase 4 — Security, Audit, Reconciliation, Resilience and Operations

## 4.1 Objective and implementation sequence

Phase 4 adds a security and operational boundary to the existing Phase 0–3 payment API. It is implemented directly inside the existing Spring Boot Payment API; an API Gateway is not required for this portfolio baseline.

The implementation sequence is:

1. **4.1 — Authentication, RSA JWT, refresh-token rotation, RBAC and ownership**
2. **4.2 — Audit logging**
3. **4.3 — Account/ledger/transfer reconciliation**
4. **4.4 — Database, Kafka and Redis resilience**
5. **4.5 — Outbox lease recovery, fencing, retry, DEAD/DLT and operational metrics**
6. **4.6 — API error handling and security/regression acceptance**
7. **4.7 — Redis-backed API rate limiting**
8. **Complete Phase 4 testing and verification**

Phase 4.7 is part of Phase 4 and is documented in this file and `docs/phase-4-api-examples.md`; it is not a separate documentation sub-project.

## 4.2 Migration policy

Phase 4 is additive:

- V4 introduces identity and reconciliation structures.
- V5 adds operations hardening fields/structures.
- V6 inserts local OPERATIONS/ADMIN test identities and role assignments.
- V1–V3 remain the original foundation, transfer and outbox migrations.

Never edit a migration that has already been applied in a shared or retained database. Add a new versioned migration for subsequent schema changes. V6 privileged credentials are local development/test fixtures only.

Verify migration history:

```sql
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

At the Phase 4 milestone, V1 through V6 are successful and in order. The current full project adds Phase 5.1 migration V7; see the canonical Phase 5 guide for the current V1–V7 sequence.

## 4.3 Sub-phase 4.1 — Authentication, JWT, refresh-token rotation, RBAC and ownership

### Design and security behavior

- Public registration creates a customer-linked user and grants only `CUSTOMER`.
- Passwords are stored as BCrypt hashes.
- Login issues an RSA/RS256-signed access JWT.
- JWT validation checks signature, issuer, expiry and configured audience.
- Access-token lifetime is 15 minutes; refresh-token lifetime is 7 days in the baseline configuration.
- Refresh tokens are stored as SHA-256 hashes and rotated within a token family. Reuse of a revoked token is treated as a token-reuse/security failure.
- Logout revokes the supplied refresh token.
- CUSTOMER access is constrained by customer/account ownership checks on customer-scoped operations.
- OPERATIONS and ADMIN can access the operational endpoints allowed by the security configuration. ADMIN-only paths, if present, remain ADMIN-only.
- There is no public endpoint for assigning privileged roles. The V6 fixture is for local testing only.

### Verification scenarios

1. Register a customer. Expected: HTTP 201 and a CUSTOMER access token.
2. Login with the registered credentials. Expected: HTTP 200 and token response.
3. Call `GET /api/v1/auth/me` with the access token. Expected: HTTP 200 and the authenticated identity.
4. Call a protected endpoint without a token. Expected: HTTP 401.
5. Call with a malformed, expired or incorrectly signed token. Expected: HTTP 401.
6. Attempt login with an incorrect password. Expected: HTTP 401.
7. Refresh a valid refresh token. Expected: HTTP 200 with a new access token and replacement refresh token.
8. Reuse the old refresh token after rotation. Expected: HTTP 401; verify token-family revocation/reuse behavior according to the identity service implementation.
9. Logout with the current refresh token. Expected: HTTP 204.
10. Attempt to refresh the logged-out token. Expected: HTTP 401.
11. Login using `operations@test.local` and `admin@test.local` seeded by V6. Save the access tokens independently as `operationsToken` and `adminToken`.
12. Call an OPERATIONS/ADMIN endpoint using a CUSTOMER token. Expected: HTTP 403.
13. Attempt to access another customer's account/transfer using a CUSTOMER token. Expected: access denied (403 or resource-hiding 404 according to the endpoint's ownership policy); verify the exact implemented contract rather than assuming one.
14. Confirm a CUSTOMER cannot obtain OPERATIONS or ADMIN by changing registration JSON.

### Database verification

```sql
SELECT id, customer_id, email, password_hash, status, created_at, updated_at
FROM users
ORDER BY created_at DESC
LIMIT 50;

SELECT u.email, r.name AS role_name
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id
ORDER BY u.email, r.name;

SELECT id, user_id, token_hash, family_id, expires_at, revoked_at, replaced_by, created_at
FROM refresh_tokens
ORDER BY created_at DESC
LIMIT 50;
```

Check that passwords are BCrypt hashes and refresh-token values are hashes, not raw credentials. Never paste token values, password hashes or private keys into documentation or issue reports.

**Pass condition:** authentication and token lifecycle work; CUSTOMER registration cannot grant privileged roles; role and ownership restrictions are enforced; secrets are stored only as hashes or protected key material.

## 4.4 Sub-phase 4.2 — Audit logging

### Documented audit actions

The implementation documents these successful actions:

- `AUTH_REGISTER`
- `AUTH_LOGIN`
- `AUTH_REFRESH`
- `AUTH_LOGOUT`
- `CUSTOMER_CREATED`
- `ACCOUNT_OPENED`
- `TRANSFER_CREATED`

Audit writes reuse the existing V1 `audit_logs` table. Failed authentication/security-event auditing is not implied unless explicitly wired by the implementation.

### Generate audit events

1. Register a customer.
2. Login.
3. Refresh the token.
4. Logout.
5. Create a customer through the customer API.
6. Open an account.
7. Create a successful transfer.

Use unique test data and retain the user/customer/account/transfer identifiers.

### Retrieve audit records

Use the OPERATIONS or ADMIN access token:

```http
GET /api/v1/audit-logs?page=0&size=25
Authorization: Bearer {{operationsToken}}
```

Expected: HTTP `200 OK`.

Retrieve a specific audit record using its ID from the list response:

```http
GET /api/v1/audit-logs/{{auditLogId}}
Authorization: Bearer {{operationsToken}}
```

Expected: HTTP `200 OK`.

Try an unknown audit ID. Expected: HTTP `404` with `AUDIT_LOG_NOT_FOUND`.

Try the list/detail endpoints using a CUSTOMER token. Expected: HTTP `403 FORBIDDEN`.

### Verify PostgreSQL records

```sql
SELECT id, actor_id, action, resource_type, resource_id,
       correlation_id, occurred_at, details
FROM audit_logs
ORDER BY occurred_at DESC
LIMIT 100;
```

Check that:

- Expected audit actions are present for the successful operations performed.
- Actor IDs are attributable to the authenticated user where applicable.
- Resource identifiers refer to the created customer/account/transfer.
- Correlation IDs are present where supplied by the implementation/request.
- `details` contains no passwords, password hashes, JWTs, refresh tokens or private keys.

**Pass condition:** successful wired operations create attributable audit records; OPERATIONS/ADMIN can read them; CUSTOMER cannot read them; no authentication secrets are persisted in audit details.

## 4.5 Sub-phase 4.3 — Account, ledger and transfer reconciliation

### Reconciliation behavior

The manual and scheduled reconciliation service checks consistency between account balance projections, ledger values, journal debit/credit totals and transfer-linked ledger entries. Runs and findings are persisted. Reconciliation is detection-only: it does not automatically repair balances or ledger entries.

### Execute and retrieve a run

1. Login as OPERATIONS or ADMIN.
2. Execute:

```http
POST /api/v1/reconciliation/run
Authorization: Bearer {{operationsToken}}
```

Expected: HTTP `200 OK` with a result containing `runId`, `status`, `accountsChecked` and `discrepanciesFound`. A clean run should have `status=COMPLETED` and `discrepanciesFound=0`.

3. List recent runs:

```http
GET /api/v1/reconciliation/runs
Authorization: Bearer {{operationsToken}}
```

Expected: HTTP `200 OK`.

4. Retrieve discrepancies for the run:

```http
GET /api/v1/reconciliation/runs/{{runId}}/discrepancies
Authorization: Bearer {{operationsToken}}
```

Expected: HTTP `200 OK` with an array; an empty array is valid for a clean run.

5. Try the endpoints with CUSTOMER credentials. Expected: HTTP `403 FORBIDDEN`.
6. Use a malformed UUID for a run ID. Expected: HTTP `400` with the common malformed-request error.
7. Use an unknown but valid UUID. The current controller queries discrepancies by `run_id`; verify the actual service/controller response for unknown IDs rather than assuming a special not-found code.

### Controlled discrepancy test

Only in a disposable local database:

1. Record the original balance for a test account.
2. Change the account balance directly in SQL without changing ledger entries.
3. Execute reconciliation.
4. Expected: the run completes and persists an `ACCOUNT_LEDGER_BALANCE` discrepancy for the account.
5. Verify the discrepancy record and its expected/actual values.
6. Restore the account balance to its original value.
7. Execute reconciliation again; expected: no discrepancy for the restored account.

Do not run this mutation against production or valuable data.

### Verify PostgreSQL records

```sql
SELECT id, status, started_at, completed_at,
       accounts_checked, discrepancies_found, error_message
FROM reconciliation_runs
ORDER BY started_at DESC
LIMIT 50;

SELECT id, run_id, discrepancy_type, resource_type, resource_id,
       expected_value, actual_value, status, detected_at
FROM reconciliation_discrepancies
ORDER BY detected_at DESC
LIMIT 100;
```

**Pass condition:** clean and discrepancy-bearing runs complete with persisted findings; operational endpoints are role-protected; reconciliation does not auto-repair financial data; execution failures do not masquerade as successful scans.

## 4.6 Sub-phase 4.4 — Database, Kafka and Redis resilience

### Configuration review

Review `application.yml` and verify the effective values, including:

- Hikari pool sizing and connection/validation timeouts.
- Redis connection and command timeouts.
- Kafka producer `acks=all`, idempotence, retries and bounded delivery/request timeouts.
- Listener retry and DLT configuration.
- Actuator liveness and readiness probes.

The baseline readiness group includes application readiness and database health. Kafka and Redis are monitored separately and are not readiness gates in this baseline. Liveness should remain independent of external dependency availability.

### PostgreSQL outage and recovery

1. Confirm application and PostgreSQL are healthy.
2. Stop PostgreSQL in a disposable local environment:

```powershell
docker compose stop postgres
```

3. Check `/actuator/health/readiness`.
4. Expected: readiness reports DOWN; database-backed requests fail within configured timeout and return sanitized HTTP `503 DEPENDENCY_UNAVAILABLE` where mapped.
5. Check `/actuator/health/liveness`.
6. Expected: liveness remains UP.
7. Restart PostgreSQL:

```powershell
docker compose up -d postgres
```

8. Expected: readiness recovers after the datasource reconnects.

### Kafka outage and recovery

1. Stop Kafka while PostgreSQL remains healthy.
2. Create a successful transfer.
3. Expected: transfer, ledger and outbox row commit; publisher cannot mark the outbox event PUBLISHED while Kafka is unavailable.
4. Restart Kafka.
5. Expected: bounded retries eventually publish the event; consumer processing resumes.

### Redis outage and recovery

Redis is used for rate limiting in Phase 4.7. Stop Redis and test the configured rate-limit failure policy in Section 4.10. Do not infer Redis dependency from the mere presence of Redis in Docker Compose; the actual application use is the rate-limit store.

### Pass condition

Dependency timeouts and retries are bounded; database failure affects readiness but not liveness; Kafka outages do not lose committed outbox events; Redis failure follows the configured rate-limit fail-open/fail-closed policy; dependencies recover without manual financial data repair.

## 4.7 Sub-phase 4.5 — Outbox lease recovery, fencing, retries, DEAD/DLT and metrics

### Operational API checks

Use OPERATIONS or ADMIN:

```http
GET /api/v1/outbox/events?page=0&size=20
Authorization: Bearer {{operationsToken}}
```

```http
GET /api/v1/outbox/events/{{outboxEventId}}
Authorization: Bearer {{operationsToken}}
```

Unknown event ID: HTTP `404 OUTBOX_EVENT_NOT_FOUND`. Malformed UUID: HTTP `400`.

### Lease recovery and fencing

1. Create an event while Kafka is unavailable or use an isolated test fixture.
2. Confirm a worker claims the event and stores processing lease information.
3. Allow the lease to expire or set `locked_until` into the past in a disposable database.
4. Trigger the next publisher claim.
5. Expected: another worker can reclaim the stale PROCESSING row and receives a new lease token/owner.
6. Attempt to mark the event published/failed using the old worker's lease token.
7. Expected: stale worker update is rejected/no row is changed; the current lease holder remains authoritative.
8. Run the `OutboxLeaseFencingTest` integration test and confirm it covers worker A claim → lease expiry → worker B reclaim → worker A stale update rejected.

The claim query should use bounded batch size and `FOR UPDATE SKIP LOCKED`, so concurrent publisher workers do not block on or claim the same eligible row.

### Retry and DEAD transition

1. Keep Kafka unavailable or use a publisher fault-injection test.
2. Allow repeated publish attempts until `app.outbox.max-attempts` is reached.
3. Expected: retry delay is bounded/capped and the row transitions to DEAD after the configured attempt limit.
4. Verify `last_error` is bounded and sanitized; it must not contain credentials or full sensitive payloads.
5. Restore Kafka and replay the DEAD event:

```http
POST /api/v1/outbox/events/{{outboxEventId}}/replay
Authorization: Bearer {{operationsToken}}
```

Expected: successful replay request returns the implementation's success response and transitions the event back to a publishable state. Replaying a non-DEAD event should return HTTP `409 INVALID_STATE`; unknown ID returns HTTP `404`.

### Kafka DLT replay

```http
POST /api/v1/operations/kafka-dlt/replay
Authorization: Bearer {{operationsToken}}
Content-Type: application/json

{
  "eventKey": "transfer-event-key",
  "payload": "{\"eventId\":\"<event-uuid>\",\"eventType\":\"TransferCompleted\"}"
}
```

Expected: successful replay returns the broker metadata response. Blank event key/payload returns HTTP `400 VALIDATION_ERROR`. Broker unavailable or send timeout returns HTTP `503 DEPENDENCY_UNAVAILABLE`. DLT replay republishes the supplied payload; it does not delete the original DLT record.

### Notification deduplication and metrics

- Deliver the same event more than once using an integration test.
- Expected: `processed_events.event_id` and notification uniqueness prevent duplicate notification effects.
- Inspect `/actuator/metrics` and the `payment.outbox.*` instruments registered by the application. Use exact metric names returned by Actuator rather than assuming names. Confirm relevant counters/gauges change after successful publication and failure/retry transitions.

**Pass condition:** claims are exclusive, expired leases recover, stale workers are fenced, retries terminate in a bounded DEAD state, replay is state-guarded, DLT replay errors are sanitized, and duplicate events do not duplicate notifications.

## Postman collection organization

The Phase 4 Postman collection uses feature-oriented folder names rather than embedding sub-phase numbers in folder names:

- `Authentication and JWT`
- `Privileged role authentication`
- `Role authorization (privileged token optional)`
- `Reconciliation`
- `Redis rate limiting`

Reconciliation run/list/discrepancy and reconciliation-specific negative authorization/UUID requests belong in `Reconciliation`. The generic role-authorization folder contains the other privileged operational endpoints, such as outbox, audit and Kafka DLT. Run OPERATIONS and ADMIN login requests separately and retain their independent token variables.

## 4.8 Sub-phase 4.6 — API errors, security regression and migration acceptance

### Common failure responses

| Condition | Expected HTTP status |
|---|---:|
| Missing/invalid bearer token on protected route | 401 |
| Authenticated user lacks required role | 403 |
| Duplicate registration email | 409 |
| Invalid login credentials | 401 |
| Invalid/expired/revoked refresh token | 401 |
| Invalid request fields | 400 |
| Unknown resource | 404 |
| Insufficient transfer funds | 422 |
| Invalid outbox state | 409 |
| Dependency unavailable/timeout | 503 |
| Unexpected server failure | 500 |

The common API error body is:

```json
{
  "code": "ERROR_CODE",
  "message": "Sanitized explanation",
  "timestamp": "2026-11-11T08:00:00Z",
  "details": {}
}
```

Verify `code`, `message`, `timestamp` and `details` are present where the shared error contract applies. Security filter-chain 401/403 responses should use the same shape. Never expose stack traces, SQL, secrets or internal infrastructure details in client responses.

### Verification procedure

1. Run `mvn clean test`.
2. Import and run the Phase 1, Phase 2, Phase 3 and Phase 4 Postman collections in sequence.
3. In Phase 4, login separately as CUSTOMER, OPERATIONS and ADMIN. Keep credentials/tokens in separate variables.
4. Verify positive and negative authorization paths, including CUSTOMER denial for audit, reconciliation, outbox and DLT operations.
5. Exercise malformed JSON/UUID, missing fields, duplicate email, invalid credentials, invalid refresh token, unknown resources, invalid replay state and dependency failure.
6. Verify Phase 1–3 financial behavior and event flow remain unchanged.
7. Verify clean-database migrations V1–V7 on the current project baseline, and retain the Phase 4-specific upgrade path from an existing V3 database.
8. Confirm V1–V7 migration files are preserved and append-only.
9. Capture test evidence: build output, Postman run summary, Flyway history, sanitized SQL results and application logs.

**Pass condition:** error statuses and response bodies are consistent; role/ownership controls hold; Phase 1–3 regression tests pass; migrations are intact; no secret leakage occurs.

## 4.9 Sub-phase 4.7 — Redis-backed API rate limiting

### Design and policy defaults

`ApiRateLimitFilter` uses `RedisRateLimitStore` and an atomic Redis Lua script to increment a fixed-window counter and set its expiry on the first request. The Redis namespace is `payment:ratelimit:v1:*`; identity material is hashed before being used in keys.

| Request class | Default limit | Identity key |
|---|---:|---|
| Login | 5 per minute | Client IP |
| Registration | 5 per hour | Client IP |
| Transfer creation | 10 per minute | Authenticated customer |
| General API | 100 per minute | Authenticated principal; IP for anonymous traffic |

Health/readiness/liveness and OPTIONS requests are excluded. Limits are configurable through `application.yml` and environment overrides.

### Response contract

A request exceeding its configured limit returns HTTP `429 Too Many Requests` with `RATE_LIMIT_EXCEEDED`, plus:

- `X-RateLimit-Limit`
- `X-RateLimit-Remaining`
- `X-RateLimit-Reset`
- `Retry-After` on a rejected request

Redis failure behavior is controlled by `app.rate-limit.fail-open` (default `true`):

- Fail-open: request continues and response includes `X-RateLimit-Status: degraded`.
- Fail-closed: request is rejected with sanitized HTTP `503 DEPENDENCY_UNAVAILABLE`.

Redis is not the source of truth for balances, ledger posting or transfer idempotency.

### Verification procedure

1. Start Redis and verify:

```powershell
docker compose exec redis redis-cli ping
```

Expected: `PONG`.

2. Confirm `app.rate-limit.enabled=true` and review effective limits in `application.yml`.
3. From one client IP, send six login requests within one minute. The first five may return HTTP 401 for intentionally invalid credentials; the sixth should return HTTP 429 `RATE_LIMIT_EXCEEDED`.
4. Send six registration requests from the same client IP within one hour using synthetic data. The sixth should return HTTP 429.
5. Authenticate as a CUSTOMER and send eleven transfer-creation requests within one minute, using isolated test accounts and unique idempotency keys. The eleventh should return HTTP 429. Ensure the requests are otherwise valid if the objective is to prove the filter blocks them before business processing.
6. Verify the `X-RateLimit-*` headers and `Retry-After` on a rejected request.
7. Verify two different authenticated customers use separate transfer buckets.
8. Stop Redis. With fail-open enabled, requests should continue and expose `X-RateLimit-Status: degraded`. With fail-open disabled in a local override, requests should return 503 `DEPENDENCY_UNAVAILABLE`.
9. Restart Redis and verify rate limiting recovers.
10. Inspect Redis keys and expiry:

```powershell
docker compose exec redis redis-cli --scan --pattern "payment:ratelimit:v1:*"
```

11. Run `mvn clean test` and repeat the Phase 1–4 regression collections.

**Pass condition:** configured per-identity thresholds are enforced; 429 response and headers are correct; Redis outage follows the selected policy; rejected requests do not reach payment business logic.

## 4.10 Complete Phase 4 execution order

Use this order to avoid confusing test state:

1. `mvn clean test`.
2. Confirm Docker services and Flyway V1–V7 on the current full project baseline.
3. Run Phase 1 collection and create reusable customer/account fixtures.
4. Run Phase 2 collection and verify successful transfer, idempotency and ledger balance.
5. Run Phase 3 collection and verify outbox publication/notifications.
6. Run Phase 4 CUSTOMER registration/login and token lifecycle.
7. Login separately as OPERATIONS and ADMIN; store separate tokens.
8. Run audit and outbox/DLT authorization tests in `Role authorization (privileged token optional)`; run all reconciliation requests in the dedicated `Reconciliation` folder.
9. Run dependency outage/recovery tests manually in a disposable environment.
10. Run rate-limit tests in the `Redis rate limiting` folder.
11. Re-run all automated tests and the Phase 1–4 Postman collections.
12. Capture evidence and record any skipped scenario with a reason.

## 4.11 Troubleshooting evidence bundle

When reporting a failure, provide:

- Full `mvn clean test` output and the first relevant `Caused by` chain.
- Request method/path, HTTP status and sanitized response body.
- Application log lines around the same timestamp.
- Active Spring profile and relevant non-secret configuration.
- `docker compose ps` and the affected dependency logs.
- Flyway history and sanitized SQL output where relevant.

Redact bearer tokens, refresh tokens, passwords, password hashes, private keys, personal data and database credentials.

## 4.12 Overall Phase 4 exit criteria

All automated tests pass; Phase 1–4 Postman positive/negative scenarios pass; role and ownership boundaries hold; reconciliation detects but does not repair discrepancies; dependency failure/recovery behavior is bounded; outbox lease/retry/replay behavior is correct; Redis rate limits and failure policy are verified; migrations remain append-only; and no secret leakage is observed.

---

> **Current project context:** This document records the scope and verification details for its named phase. The current overall project status is maintained in the root [README](../README.md). Phase 5 observability, performance, deployment, CI/CD and current operational status are consolidated in the [single Phase 5 source of truth](phase-5.md).
