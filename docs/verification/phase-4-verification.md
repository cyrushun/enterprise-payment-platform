# Phase 4 — Verification index and acceptance checklist

This is the concise phase gate. The [complete Phase 4.1–4.6 guide](phase-4-complete-testing-guide.md) contains detailed commands, API steps, expected status/error codes, SQL checks and controlled fault injection.

| Sub-phase | Required verification | Expected outcome |
|---|---|---|
| 4.1 Identity/JWT/RBAC/ownership | Register/login/me, JWT tamper/expiry, refresh reuse, logout, role and cross-customer access | 201/200 success; 401 unauthenticated; 403 denied; refresh reuse rejected |
| 4.2 Audit | Successful wired actions, privileged list/detail, unknown ID, customer denial, sensitive-data inspection | Durable attributable audit records; 404/403; no secrets in details |
| 4.3 Reconciliation | Clean run, discrepancy fixture, DB/query failure, malformed UUID, scheduled execution | Manual result only after completion; discrepancies persisted; failed scan not reported as success |
| 4.4 Resilience | Stop/restart PostgreSQL, Kafka, Redis; verify bounds, readiness/liveness and recovery | Sanitized 503 for synchronous dependency failures; outbox retained; liveness isolated |
| 4.5 Outbox operations | Multi-worker claim, stale lease, fencing, retries, DEAD, replay, DLT replay and metrics | Stale worker rejected; bounded retry; DEAD replay guarded; Kafka failure 503 |
| 4.6 Regression | Maven suite, Testcontainers, Postman 1–4, migration V1–V5 and secret-leak checks | All tests pass and prior phase contracts remain intact |

## Sign-off record

For each row record: build/commit ID, date/time, environment, tester, PASS/FAIL/BLOCKED, evidence path and defect ID. A test that was not executed is BLOCKED, not PASS. Never run corruption/fault-injection SQL against a shared or production database.

## Release gate

- [ ] `mvn clean test` passes on JDK 21.
- [ ] Clean DB applies V1–V5 successfully; existing V4 DB upgrades to V5.
- [ ] Phase 1–3 regression collections pass.
- [ ] Phase 4 positive and negative Postman requests pass.
- [ ] Dependency outage and recovery scenarios pass in disposable local environment.
- [ ] No stack trace, SQL, secret, token or password hash leaks in API responses/logged request bodies.
- [ ] README, phase docs, API examples and verification guides describe the delivered implementation.
