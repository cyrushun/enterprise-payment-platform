# Phase 4 — API Examples

Base URL: `http://localhost:8080`.

## 1. Register a customer

```http
POST /api/v1/auth/register HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "firstName": "Alex",
  "lastName": "Tan",
  "email": "alex.tan@example.test",
  "password": "LocalOnly-Passw0rd!",
  "phoneNumber": "+60123456789"
}
```

Expected: `201 Created`, with `accessToken`, `refreshToken`, `expiresIn`, and `tokenType`. Public registration assigns only `CUSTOMER`.

## 2. Login

```http
POST /api/v1/auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "email": "alex.tan@example.test",
  "password": "LocalOnly-Passw0rd!"
}
```

Expected: `200 OK` and a token response. Invalid credentials return `401 Unauthorized`.

## 3. Get current identity

```http
GET /api/v1/auth/me HTTP/1.1
Host: localhost:8080
Authorization: Bearer <accessToken>
```

Expected: `200 OK` with `userId`, `customerId`, and `email`.

## 4. Refresh token

```http
POST /api/v1/auth/refresh HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "refreshToken": "<refreshToken>"
}
```

Expected: `200 OK` with a replacement access and refresh token. Store the new refresh token securely and discard the old one. Reusing a revoked token is treated as a reuse attempt.

## 5. Logout

```http
POST /api/v1/auth/logout HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "refreshToken": "<currentRefreshToken>"
}
```

Expected: `204 No Content`.

## 6. Authorization headers

For protected routes:

```http
Authorization: Bearer <accessToken>
```

The current security configuration restricts `/api/v1/admin/**` to `ADMIN`, and `/api/v1/audit-logs/**`, `/api/v1/reconciliation/**`, and `/api/v1/outbox/**` to `OPERATIONS` or `ADMIN`. The reconciliation manual-run endpoint returns a completed run result synchronously; scheduled execution remains an asynchronous job boundary.

## 7. Common failure responses

| Condition | Expected HTTP status |
|---|---:|
| Missing/invalid bearer token on protected route | 401 |
| Authenticated user lacks required role | 403 |
| Duplicate registration email | 409 |
| Invalid login credentials | 401 |
| Invalid/expired/revoked refresh token | 401 |
| Invalid request fields | 400 |

Errors use `{ "code", "message", "timestamp", "details" }`. Database/Kafka dependency failures and DLT replay timeouts return 503 `DEPENDENCY_UNAVAILABLE`; invalid outbox state returns 409 `INVALID_STATE`; malformed UUID/JSON returns 400 `MALFORMED_REQUEST`; unexpected failures return sanitized 500 `INTERNAL_ERROR`.


## Phase 4.2–4.5 operations examples

```http
GET /api/v1/audit-logs?page=0&size=25
Authorization: Bearer <operations-or-admin-token>

GET /api/v1/reconciliation/runs
Authorization: Bearer <operations-or-admin-token>

POST /api/v1/reconciliation/run
Authorization: Bearer <operations-or-admin-token>

# Successful synchronous response shape
{ "runId": "<uuid>", "status": "COMPLETED", "accountsChecked": 12, "discrepanciesFound": 0 }

POST /api/v1/outbox/events/<dead-event-id>/replay
Authorization: Bearer <operations-or-admin-token>

POST /api/v1/operations/kafka-dlt/replay
Authorization: Bearer <operations-or-admin-token>
Content-Type: application/json

{ "eventKey": "<original-key>", "payload": "<original-json-string>" }
```

Kafka DLT replay re-publishes the payload to the configured transfer topic and preserves the original DLT record. Notification idempotency should be relied on for duplicate delivery safety.
