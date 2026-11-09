# Verification and Testing Strategy — All Phases

## Local test sequence

```powershell
java -version
mvn -version
docker compose up -d
docker compose ps
mvn clean test
mvn spring-boot:run
```

Do not overwrite an existing `.env`. Confirm local-only RSA key material is being used. Check `/actuator/health`, `/actuator/health/readiness`, and `/actuator/health/liveness`.

## Phase acceptance plans

- Phase 0: [Foundation verification](verification/phase-0-verification.md)
- Phase 1: [Customer/account verification](verification/phase-1-verification.md)
- Phase 2: [Transfer/idempotency/ledger verification](verification/phase-2-verification.md)
- Phase 3: [Outbox/notification verification](verification/phase-3-verification.md)
- Phase 4: [Phase 4 gate](verification/phase-4-verification.md) and [complete 4.1–4.6 runbook](verification/phase-4-complete-testing-guide.md)

## Postman collections

Import the Phase 1, Phase 2, Phase 3 and Phase 4 collections with `Enterprise-Payment-Platform-Local.postman_environment.json`. Run earlier phase collections after later phases to detect regressions. Phase 4 privileged operations require a dedicated OPERATIONS/ADMIN token; public registration grants CUSTOMER only. Do not store real bearer tokens in exported collections.

## Error contract

All API failures should have `code`, `message`, `timestamp`, and `details`. Validate status/code pairs in the API collection. For dependency failure, expected HTTP 503 `DEPENDENCY_UNAVAILABLE`; uniqueness conflicts remain HTTP 409; unexpected failures return sanitized HTTP 500 `INTERNAL_ERROR`.

## Test evidence

Retain Maven logs, Postman run export, migration history, sanitized SQL evidence, Compose status and application logs. Mark unexecuted tests BLOCKED, not PASS. Perform destructive data fixtures only on disposable local databases.
