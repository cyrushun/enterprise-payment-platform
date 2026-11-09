# Phase 4 — Security, Audit, Reconciliation and Resilience

## 1. Objective

Introduce an identity and security foundation, establish a place to record audit activity, and add an initial scheduled account-to-ledger reconciliation scan while preserving the Phase 0–3 transaction and event foundations.

**Implementation status:** this baseline includes Phase 4.1–4.6 application features and operational verification artifacts. It remains a local portfolio implementation, not a production banking system. Execute the complete runbook and record evidence before declaring the phase verified.

## 2. Database migration

Phase 4 adds `V4__phase4_identity_reconciliation.sql` and `V5__phase4_operations_hardening.sql`. V5 adds outbox lease fencing columns/indexes and reconciliation lookup indexes. It does not edit V1–V4.

V4 adds:
- `users`, linked optionally to a `customers` row;
- `roles` and `user_roles`, with `CUSTOMER`, `OPERATIONS`, and `ADMIN` seed roles;
- `refresh_tokens`, storing SHA-256 token hashes and family identifiers;
- `reconciliation_runs` and `reconciliation_discrepancies`.

Flyway applies V4 and V5 after V3 on application startup. Never modify an already-applied migration; add a new versioned migration for future schema changes.

## 3. Authentication and authorization (4.1)

- Public customer registration creates a customer and a user, assigns only the `CUSTOMER` role, and returns access and refresh tokens.
- Login verifies the BCrypt password hash and issues an RSA/RS256-signed JWT.
- JWT validation checks signature, issuer, expiry and configured audience.
- Access tokens carry the user UUID as `sub`, role names in `roles`, and `customer_id` when the user is linked to a customer.
- Refresh tokens are stored as SHA-256 hashes. Refresh rotates the old token and issues a replacement in the same token family.
- Logout revokes the supplied refresh token.
- The security filter chain is stateless. The default policy requires authentication except for health and registration/login/refresh routes.
- `/api/v1/admin/**` is restricted to `ADMIN`; outbox, audit and reconciliation route prefixes are restricted to `OPERATIONS` or `ADMIN`.

The `OwnershipService` provides account and transfer ownership assertions. It must be called from the relevant application service/controller paths before customer-scoped data is returned or changed. Merely defining this helper does not automatically enforce ownership on existing endpoints.

## 4. Audit (4.2)

Audit writes use the existing V1 `audit_logs` table through `AuditService`; no replacement audit table is introduced. The implemented event coverage is the successful identity lifecycle and successful customer/account/transfer actions explicitly wired to `AuditService`. Operations/ADMIN can query `/api/v1/audit-logs` and `/api/v1/audit-logs/{id}`. This does not mean every rejected request is automatically audited: failed authentication/security-event auditing is a separate policy and should be added deliberately without recording credentials or token material.

Audit reads and writes must not expose passwords, password hashes, access tokens, refresh tokens, private keys or raw database exception details. A missing audit record returns `404` with `AUDIT_LOG_NOT_FOUND`; unauthorized roles return `403` through the Spring Security access-denied handler.

## 5. Reconciliation (4.3)

The scheduled/manual read-only scan compares account balances with ledger net values, checks each journal's debit/credit equality, and checks transfer amounts against linked debit and credit entries. Runs and discrepancies persist in the V4 reconciliation tables. Operations/ADMIN endpoints are `POST /api/v1/reconciliation/run`, `GET /api/v1/reconciliation/runs`, and `GET /api/v1/reconciliation/runs/{runId}/discrepancies`. Reconciliation detects and persists discrepancies; it does not automatically repair account balances or posted ledger entries.

## 6. Resilience and outbox hardening (4.4–4.5)

PostgreSQL Hikari timeouts, Redis connect/command timeouts, Kafka producer acknowledgements/idempotence/retries, listener retry/DLT configuration and Actuator probes are configured in `application.yml`. Readiness includes `readinessState` and `db`; liveness includes only `livenessState`. Kafka and Redis are monitored separately and are not readiness gates in this baseline.

Outbox records use leases and lease-token fencing. Expired PROCESSING records can be reclaimed; stale lease holders cannot update an event after another worker claims it. Failed publishes are retried and eventually become DEAD after the configured attempt limit. Operations can inspect and replay DEAD records through `/api/v1/outbox/events`; Kafka DLT replay is exposed through `/api/v1/operations/kafka-dlt/replay`. Metrics are emitted for publisher outcomes where the corresponding meter is registered. These operational flows require the integration/failure tests in the complete Phase 4 runbook.

## 7. Error handling and API exception contract

Controller exceptions are normalized by `GlobalExceptionHandler` into the shared `ApiError` record (`code`, `message`, `timestamp`, `details`). Spring Security filter-chain failures bypass MVC controller advice, so `SecurityConfiguration` separately writes the same response shape for `401` and `403`. Validation, malformed requests, not-found, conflict, invalid domain operations, persistence failures, Kafka replay timeouts/broker failures and unexpected failures have explicit mappings. Database/Kafka dependency failures return 503; unexpected reconciliation/programming failures return 500. Integrity/uniqueness conflicts remain 409. Unexpected failures return a generic `500 INTERNAL_ERROR`; stack traces and internal exception messages remain in server logs, not client responses.

Scheduled jobs and asynchronous Kafka/outbox processing do not have a synchronous HTTP caller to receive an exception. Their failures must be observed through logs, persisted run/event state and metrics. An HTTP request that triggers an operation can receive a normalized error only for failures occurring synchronously before the response is completed.

## 8. Security/regression verification (4.6)

Run `mvn clean test`, then follow [Complete Phase 4 Testing Guide (4.1–4.6)](verification/phase-4-complete-testing-guide.md). It covers positive and negative JWT tests, refresh rotation/reuse, RBAC and ownership, audit SQL checks, reconciliation discrepancy tests, dependency outage/recovery, outbox lease fencing/retry/DEAD/replay, DLT replay, metrics and Phase 0–3 regression.

## 8. Configuration and key management

JWT configuration is under `app.security.jwt` in `src/main/resources/application.yml`. Local test key material is under `src/test/resources/keys` and is for tests only. Supply production public/private keys through a secret manager or mounted secret files; never commit production private keys. Rotate keys with an explicit key-id/JWKS strategy before production deployment.

## 9. API references

See [Phase 4 API examples](phase-4-api-examples.md). The currently implemented public identity routes are:
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`

No admin user/role management controller or reconciliation query API is included in this implementation snapshot.

## Phase 4.2–4.6 implementation scope

### 4.2 Audit logging
Identity registration/login/refresh/logout, customer creation, account opening, and successful transfer creation record action, actor, resource, correlation context, and non-sensitive structured details into the existing `audit_logs` table. Privileged read APIs are available at `/api/v1/audit-logs`.

### 4.3 Reconciliation
The scheduled scan compares account balances with ledger net values, each journal's debit and credit totals, and transfer amounts with linked ledger entries. Run/discrepancy results persist in the V4 reconciliation tables. Manual trigger and result endpoints are operations-only. Manual execution returns a run result only after the scan completes; execution failures propagate to the API exception mapper, while the scheduled trigger logs failures at its asynchronous boundary. Discrepancy findings are successful detection results, not HTTP errors. Reconciliation is observational and never repairs balances automatically.

### 4.4 Resilience
PostgreSQL connection and validation timeouts, Redis connect/command timeouts, Kafka producer acknowledgement/idempotence/retry settings, listener retry and DLT handling, and actuator readiness/liveness are configured. Tune values by environment.

### 4.5 Outbox operations
Claims are leased and fenced by a generated lease token. Expired PROCESSING rows are reclaimable; only the current lease holder may update state. Failed publishes retry with capped exponential delay and become DEAD after max attempts. Operations may inspect and replay DEAD rows. Kafka listener DLT payloads can also be re-published through the guarded `/api/v1/operations/kafka-dlt/replay` operation; replay does not erase the original DLT record. Micrometer counters track successful/failed publishing.

### 4.6 Security and regression verification
Run `mvn clean test`, start the Docker Compose baseline, and follow `docs/verification/phase-4-verification.md`. Import `Enterprise-Payment-Platform-Phase4.postman_collection.json`; use a CUSTOMER token for denial tests and an OPERATIONS/ADMIN token for audit/reconciliation/outbox administration.

## Complete test runbook

See [Complete Phase 4 Testing Guide (4.1–4.6)](verification/phase-4-complete-testing-guide.md) for exact PowerShell commands, API scenarios, expected statuses/error codes, SQL verification and failure-injection steps.
