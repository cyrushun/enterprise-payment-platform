# Phase 4 — Security, Operations and Rate-Limiting API Testing Guide

Base URL: `http://localhost:8080`.

## Prerequisites and Postman variables

- Flyway has applied V1–V6.
- PostgreSQL, Kafka and Redis are running.
- Import `Enterprise-Payment-Platform-Phase4.postman_collection.json` and the local environment.
- Use separate role credentials and tokens. Do not reuse a generic `token` variable for privileged requests.

The checked-in Phase 4 collection uses these collection variables. Keep the CUSTOMER variables separate from the privileged-role variables; do not overwrite one role's token with another role's token.

| Variable | Purpose |
|---|---|
| `baseUrl` | `http://localhost:8080` |
| `email`, `password`, `accessToken`, `refreshToken`, `previousRefreshToken` | CUSTOMER authentication and token lifecycle |
| `operationsEmail`, `operationsPassword`, `operationsToken`, `operationsRefreshToken` | OPERATIONS login and privileged endpoints |
| `adminEmail`, `adminPassword`, `adminToken`, `adminRefreshToken` | ADMIN login and privileged endpoints |
| `outboxEventId`, `deadOutboxEventId`, `nonDeadOutboxEventId`, `reconciliationRunId` | IDs captured by the relevant Postman tests |

### Postman folder organization

The Phase 4 collection is organized by feature, not by sub-phase number:

```text
Enterprise Payment Platform - Phase 4 API Tests
├── Authentication and JWT
├── Privileged role authentication
├── Role authorization (privileged token optional)
├── Reconciliation
└── Redis rate limiting
```

- **Authentication and JWT** contains CUSTOMER registration, login, identity, refresh and logout scenarios.
- **Privileged role authentication** contains separate OPERATIONS and ADMIN login requests. Each request saves its access and refresh tokens to role-specific variables.
- **Role authorization (privileged token optional)** contains the outbox, audit and Kafka DLT authorization/operations scenarios that are not reconciliation endpoints.
- **Reconciliation** contains the run-list, execute-run, discrepancy-retrieval, CUSTOMER-denial and malformed-run-ID requests. Execute a reconciliation run before running the discrepancies request; the run request stores `reconciliationRunId` when the response provides it.
- **Redis rate limiting** contains the login and general API rate-limit probes. Repeat the login probe six times from the same client IP within one minute.

Run the two privileged login requests before the OPERATIONS/ADMIN endpoint tests. The OPERATIONS and ADMIN tokens are intentionally independent. Use the CUSTOMER `accessToken` for CUSTOMER-denial tests and the `operationsToken` or `adminToken` for privileged requests.

V6 local fixture credentials:

- OPERATIONS: `operations@test.local` / `OperationsTest123!`
- ADMIN: `admin@test.local` / `AdminTest123!`

These are local test fixtures only. Never use these credentials outside a disposable development environment.

## Phase 4.1 — Authentication and token lifecycle

### 1. Register CUSTOMER

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "firstName": "Alex",
  "lastName": "Tan",
  "email": "{{customerEmail}}",
  "password": "{{customerPassword}}",
  "phoneNumber": "+60123456789"
}
```

Expected: HTTP `201 Created`; response contains access token, refresh token, expiry and token type. Public registration assigns only CUSTOMER.

### 2. Login CUSTOMER

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "{{customerEmail}}",
  "password": "{{customerPassword}}"
}
```

Expected: HTTP `200 OK`. Save access and refresh tokens in the CUSTOMER variables.

### 3. Login OPERATIONS and ADMIN independently

OPERATIONS:

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "{{operationsEmail}}",
  "password": "{{operationsPassword}}"
}
```

ADMIN:

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "{{adminEmail}}",
  "password": "{{adminPassword}}"
}
```

Expected: HTTP `200 OK` for each. Save the returned tokens separately as `operationsToken` and `adminToken`. Do not overwrite `customerToken`.

### 4. Current identity

```http
GET /api/v1/auth/me
Authorization: Bearer {{customerToken}}
```

Expected: HTTP `200 OK` with the authenticated user identity.

### 5. Refresh rotation

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "{{customerRefreshToken}}"
}
```

Expected: HTTP `200 OK` with replacement tokens. Update the Postman variables to the new tokens and discard the old refresh token.

Replay the old refresh token. Expected: HTTP `401 Unauthorized` because it has been rotated/revoked; verify family reuse handling against the identity-service implementation.

### 6. Logout

```http
POST /api/v1/auth/logout
Content-Type: application/json

{
  "refreshToken": "{{customerRefreshToken}}"
}
```

Expected: HTTP `204 No Content`. Attempt to refresh the logged-out token: HTTP `401 Unauthorized`.

## Phase 4.2 — Audit logging

### Generate audit events

Perform these successful actions:

1. Register a customer.
2. Login.
3. Refresh the token.
4. Logout.
5. Create a customer.
6. Open an account.
7. Create a successful transfer.

The documented actions are `AUTH_REGISTER`, `AUTH_LOGIN`, `AUTH_REFRESH`, `AUTH_LOGOUT`, `CUSTOMER_CREATED`, `ACCOUNT_OPENED` and `TRANSFER_CREATED`.

### Retrieve audit records

```http
GET /api/v1/audit-logs?page=0&size=25
Authorization: Bearer {{operationsToken}}
```

Expected: HTTP `200 OK`.

Retrieve one audit record:

```http
GET /api/v1/audit-logs/{{auditLogId}}
Authorization: Bearer {{operationsToken}}
```

Expected: HTTP `200 OK`.

Unknown audit ID: expected HTTP `404 AUDIT_LOG_NOT_FOUND`. CUSTOMER token: expected HTTP `403 FORBIDDEN`.

### Verify PostgreSQL

```sql
SELECT id, actor_id, action, resource_type, resource_id,
       correlation_id, occurred_at, details
FROM audit_logs
ORDER BY occurred_at DESC
LIMIT 100;
```

Verify expected actions, actor/resource attribution and correlation IDs where supplied. Confirm no password, password hash, JWT, refresh token or private key appears in `details`.

## Phase 4.3 — Reconciliation

Run the requests in the Postman **Reconciliation** folder. The folder contains the reconciliation list, execute, discrepancies and negative authorization/UUID scenarios. It is intentionally separate from **Role authorization (privileged token optional)** because these requests belong to the reconciliation feature.

### Execute and retrieve a run

```http
POST /api/v1/reconciliation/run
Authorization: Bearer {{operationsToken}}
```

Expected: HTTP `200 OK` with `runId`, `status`, `accountsChecked` and `discrepanciesFound`.

```http
GET /api/v1/reconciliation/runs
Authorization: Bearer {{operationsToken}}
```

Expected: HTTP `200 OK`.

```http
GET /api/v1/reconciliation/runs/{{runId}}/discrepancies
Authorization: Bearer {{operationsToken}}
```

Expected: HTTP `200 OK` with an array; empty is valid when no discrepancy exists.

CUSTOMER token: HTTP `403 FORBIDDEN`. Malformed UUID: HTTP `400`.

### Controlled discrepancy verification

In a disposable database only, change one test account balance without changing its ledger, execute reconciliation, and verify an `ACCOUNT_LEDGER_BALANCE` finding. Restore the original balance and rerun.

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

Reconciliation detects and persists findings; it does not auto-repair financial data.

## Phase 4.4 — Health and dependency behavior

```http
GET /actuator/health/liveness
GET /actuator/health/readiness
```

Expected: both are UP when the application and database are healthy. Stop PostgreSQL in a disposable environment: readiness should become DOWN while liveness remains UP. Restart PostgreSQL and verify readiness recovers.

Stop Kafka and create a transfer: transfer/outbox persistence should succeed while publication retries. Restore Kafka and verify eventual publication.

Redis outage behavior is tested under Phase 4.7.

## Phase 4.5 — Outbox operations and DLT

### List/get outbox

```http
GET /api/v1/outbox/events?page=0&size=20
Authorization: Bearer {{operationsToken}}
```

```http
GET /api/v1/outbox/events/{{outboxEventId}}
Authorization: Bearer {{operationsToken}}
```

Expected: HTTP `200 OK`. Unknown ID: HTTP `404 OUTBOX_EVENT_NOT_FOUND`; malformed UUID: HTTP `400`.

### Replay DEAD event

```http
POST /api/v1/outbox/events/{{outboxEventId}}/replay
Authorization: Bearer {{operationsToken}}
```

Expected: success for a DEAD event; HTTP `409 INVALID_STATE` for a non-DEAD event; HTTP `404` for unknown ID.

### Replay DLT payload

```http
POST /api/v1/operations/kafka-dlt/replay
Authorization: Bearer {{operationsToken}}
Content-Type: application/json

{
  "eventKey": "transfer-event-key",
  "payload": "{\"eventId\":\"<event-uuid>\",\"eventType\":\"TransferCompleted\"}"
}
```

Expected: successful broker send returns broker metadata. Blank payload/key: HTTP `400 VALIDATION_ERROR`. Broker unavailable/send timeout: HTTP `503 DEPENDENCY_UNAVAILABLE`.

### Verify lease/retry behavior

Use integration tests and a disposable database to verify bounded batch claims, `FOR UPDATE SKIP LOCKED`, expired lease recovery, new lease token issuance, stale-worker fencing, bounded retry/backoff, DEAD transition, replay state validation and `payment.outbox.*` metrics.

## Phase 4.6 — Common error responses

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

The common body contains `code`, `message`, `timestamp` and `details`. Verify the body is sanitized and contains no stack trace, SQL or secrets.

## Phase 4.7 — Redis-backed rate limiting

Run the probes in the Postman **Redis rate limiting** folder. The folder name is feature-oriented and deliberately does not include the sub-phase number.

Defaults:

| Request class | Default limit | Identity |
|---|---:|---|
| Login | 5/minute | Client IP |
| Registration | 5/hour | Client IP |
| Transfer creation | 10/minute | Authenticated customer |
| General API | 100/minute | Principal, or IP for anonymous |

### Login limit test

Send six invalid login requests from the same client IP within one minute.

Expected: first five requests can return HTTP `401` for invalid credentials; the sixth returns HTTP `429 RATE_LIMIT_EXCEEDED`.

### Registration limit test

Send six registration requests from the same IP within one hour using synthetic email addresses. Expected: sixth request returns HTTP `429 RATE_LIMIT_EXCEEDED`.

### Transfer limit test

As one CUSTOMER, send eleven otherwise-valid transfer creation requests within one minute, with unique idempotency keys and isolated accounts. Expected: eleventh request returns HTTP `429 RATE_LIMIT_EXCEEDED`.

### Headers and Redis failure

On normal limited requests, inspect:

- `X-RateLimit-Limit`
- `X-RateLimit-Remaining`
- `X-RateLimit-Reset`

On HTTP 429, also inspect `Retry-After`.

Stop Redis. With `app.rate-limit.fail-open=true` (default), requests continue and include `X-RateLimit-Status: degraded`. With fail-open disabled, expect HTTP `503 DEPENDENCY_UNAVAILABLE`. Restart Redis and verify normal limiting resumes.

Check keys:

```powershell
docker compose exec redis redis-cli --scan --pattern "payment:ratelimit:v1:*"
```

Keys should expire automatically. Do not use Redis as the financial system of record.

## Final Phase 4 regression and pass condition

1. Run `mvn clean test`.
2. Run Phase 1, 2, 3 and 4 Postman collections.
3. Verify CUSTOMER, OPERATIONS and ADMIN tokens remain separate and each role has the expected access.
4. Verify all successful and negative scenarios above.
5. Verify V1–V6 migration history and preserve migration files.
6. Confirm no secrets are present in API errors, audit details, logs or Postman exports.

Phase 4 passes when automated tests and applicable Postman scenarios pass, security boundaries hold, audit/reconciliation records are correct, dependency/outbox behavior is recoverable, rate limits work, and Phase 1–3 financial/event behavior remains intact.
