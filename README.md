# Enterprise Payment Platform

A portfolio-grade, simulated banking and payment platform demonstrating senior-level Java engineering: transactional correctness, concurrency control, idempotency, double-entry accounting, transactional outbox/event-driven integration, identity/security, auditability, reconciliation and operational resilience.

> **Disclaimer:** Educational simulation only. It is not intended to hold, transfer, or process real customer funds.

## Current project status

| Phase | Scope | Status in this repository |
|---|---|---|
| 0 | Foundation, architecture, requirements, initial schema and local infrastructure | Implemented |
| 1 | Customers, accounts and balance APIs | Implemented |
| 2 | Transfers, double-entry ledger, concurrency and idempotency | Implemented; regression verification required after later changes |
| 3 | Transactional outbox, Kafka consumer/publisher and notifications | Implemented; operational hardening remains |
| 4.1–4.6 | JWT/RBAC, audit, reconciliation, dependency resilience, outbox operations and API error handling | Implemented in baseline; execute the Phase 4 runbook locally before marking verified |
| 4.7 | Redis-backed API rate limiting | Implemented in baseline; execute the Phase 4.7 verification checks locally before marking verified |

Do not interpret a phase label as production readiness. The detailed phase docs identify implemented behavior and outstanding work.

## Technology baseline

| Area | Choice |
|---|---|
| Language | Java 21 LTS |
| Framework | Spring Boot 4.0.0 |
| Build | Maven |
| Persistence | Spring Data JPA / Hibernate, JDBC where appropriate |
| Database | PostgreSQL 17 |
| Schema migration | Flyway |
| Rate limiting / ephemeral counters | Redis 7 (Phase 4.7) |
| Messaging | Apache Kafka (KRaft mode in local Compose) |
| Security | Spring Security, RSA/RS256 JWT, BCrypt |
| Testing | JUnit 5, Mockito, Testcontainers |
| Local environment | Docker Desktop / Docker Compose |

Versions are pinned in `pom.xml` and `docker-compose.yml`.

## Repository structure

```text
enterprise-payment-platform/
├── README.md
├── pom.xml
├── docker-compose.yml
├── .env.example
├── AGENTS.md
├── docs/
│   ├── phase-0.md
│   ├── phase-1.md
│   ├── phase-1-api-examples.md
│   ├── phase-2.md
│   ├── phase-2-api-examples.md
│   ├── phase-3.md
│   ├── phase-3-api-examples.md
│   ├── phase-4.md
│   ├── phase-4-api-examples.md
│   ├── phase-4.7.md
│   ├── phase-4.7-api-examples.md
│   ├── verification/
│   │   ├── phase-0-verification.md ... phase-3-verification.md
│   │   └── phase-4-complete-testing-guide.md
│   ├── requirements/
│   ├── architecture/
│   ├── database/
│   ├── api/
│   └── interview/
├── src/main/java/com/enterprise/payment/
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
└── src/test/java/com/enterprise/payment/
```

## Prerequisites

- JDK 21
- Maven 3.9+
- Docker Desktop with Docker Compose
- Git
- Optional: IntelliJ IDEA and OpenCode

Verify:

```powershell
java -version
mvn -version
docker version
docker compose version
```

## Quick start — Windows PowerShell

1. Configure local environment. If `.env.example` exists, copy it:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

2. Start dependencies:

```powershell
docker compose up -d
docker compose ps
```

3. Run the automated test suite:

```powershell
mvn clean test
```

4. Start the application:

```powershell
mvn spring-boot:run
```

5. Check health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

See the [Phase 4 complete testing guide](docs/phase-4.md) and the [Phase 4 complete testing guide](docs/phase-4.md) for migration checks, API smoke tests, regression tests and failure scenarios.

## Database migration policy

Flyway migrations are append-only. The current migration sequence is:

- V1 — foundational customers, accounts, transfers, ledger, idempotency, outbox and audit schema.
- V2 — Phase 2 transfer-integrity schema changes.
- V3 — Phase 3 outbox, processed-event and notification changes.
- V4 — Phase 4 users, roles, refresh tokens and reconciliation tables.
- V5 — Phase 4 operations hardening, outbox lease fencing and operational indexes.
- V6 — local OPERATIONS and ADMIN test users/role assignments; development verification fixture only.

**Never edit an applied migration (V1–V6).** Add a new migration with the next version for schema changes. Verify migration history with:

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## Security and secrets

Local/test RSA keys are not production credentials. Use a secret manager or protected mounted files for production key material. Do not commit private keys, `.env` secrets, access tokens, refresh tokens or real customer data. Public registration grants only the `CUSTOMER` role.

## Phase documentation

- [Phase 0 — Foundation](docs/phase-0.md)
- [Phase 1 — Customers and Accounts](docs/phase-1.md) · [API examples](docs/phase-1-api-examples.md)
- [Phase 2 — Transfers and Ledger](docs/phase-2.md) · [API examples](docs/phase-2-api-examples.md)
- [Phase 3 — Outbox, Kafka and Notifications](docs/phase-3.md) · [API examples](docs/phase-3-api-examples.md)
- [Phase 4 — Security, Audit, Reconciliation and Resilience (4.1–4.7)](docs/phase-4.md) · [API examples](docs/phase-4-api-examples.md) · [Phase 4.7 rate limiting](docs/phase-4.md) · [Rate-limit API examples](docs/phase-4-api-examples.md)
- [Phase 4 complete testing guide](docs/phase-4.md)
- [Phase 4 complete testing guide](docs/phase-4.md)

## Development rules

1. Read `AGENTS.md` and relevant phase docs before changing code.
2. Every phase implementation must update its phase design/scope document, API examples when endpoints or payloads change, README status/links, and its verification plan or the consolidated Phase 4 runbook.
3. Add tests for each new business/security behavior.
4. Use new Flyway migrations; never rewrite applied migrations.
5. Preserve existing API contracts unless a documented, versioned change is intended.
6. Never mutate posted ledger entries; use compensating entries.
7. Do not log credentials, tokens, or sensitive payment data.
8. Record material architecture changes as ADRs.

## Documentation and verification

- API contract overview: `docs/api/api-specification.md`
- Architecture: `docs/architecture/`
- Phase guides: `docs/phase-0.md` through `docs/phase-4.md`, plus `docs/phase-4.md` for the separately documented rate-limiting sub-phase
- Phase API examples: `docs/phase-1-api-examples.md` through `docs/phase-4-api-examples.md`, plus `docs/phase-4-api-examples.md`
- Per-phase verification plans: `docs/phase-0.md` through `phase-3-verification.md`; Phase 4 uses one consolidated testing and verification guide.
- Complete Phase 4 runbook: `docs/phase-4.md` (includes Phase 0–3 regression checks)
- Postman collections: Phase 1, Phase 2, Phase 3, and `Enterprise-Payment-Platform-Phase4.postman_collection.json`

Phase 4 Postman coverage includes CUSTOMER registration/login/refresh/logout, separate OPERATIONS and ADMIN login/token variables (`operationsEmail`/`operationsPassword`/`operationsToken`; `adminEmail`/`adminPassword`/`adminToken`), RBAC, reconciliation success and discrepancy lookup, malformed IDs, outbox 404/409, validation, Kafka DLT authorization and rate limiting. The role-specific variables are collection variables so the local environment does not shadow newly issued tokens. Dependency outage scenarios are run manually using the complete guide. Follow the [Phase 4 complete testing guide](docs/phase-4.md) for 4.1–4.7 test steps, expected error codes, SQL checks, resilience scenarios and regression runs. Documentation describes intended checks; execute them locally and record results before claiming verification.

### Phase 4 operations scope (4.2–4.7)

Phase 4 now includes audit events, account/ledger/transfer reconciliation with persisted discrepancies, dependency timeouts/readiness checks, leased/fenced outbox retry and DEAD-event replay operations, and expanded positive/negative Postman verification. Manual reconciliation now propagates scan failures rather than returning a false success response; Kafka DLT timeout/broker failures map to sanitized 503 responses. See `docs/phase-4.md`, `docs/phase-4.md`, `docs/api/api-specification.md`, `docs/architecture/system-architecture.md`, and `docs/phase-4.md`.

**Development JWT keys:** `src/main/resources/keys/jwt-public.pem` and `jwt-private.pem` are the supplied local development pair. Never deploy this private key to production; configure external key resources or a managed key service.


## Phase 4.7 — Redis-backed API rate limiting

Redis is now used by the application for atomic, expiring API request counters. The default policies protect login, registration, transfer creation and general API traffic. See [Phase 4.7](docs/phase-4.md) and the [Phase 4 complete testing guide](docs/phase-4.md). Redis remains non-authoritative and is never used for financial balances, ledger posting or transfer idempotency.


## Phase documentation and test guides

Each phase document contains its implementation scope, technical design, verification scenarios, expected outcomes and pass conditions. API-specific request examples and executable API/Postman scenarios are maintained alongside it.

- [Phase 0 — Foundation and environment verification](docs/phase-0.md)
- [Phase 1 — Customer/account API and testing guide](docs/phase-1.md) · [API guide](docs/phase-1-api-examples.md)
- [Phase 2 — Transfer, ledger and idempotency verification](docs/phase-2.md) · [API guide](docs/phase-2-api-examples.md)
- [Phase 3 — Outbox, Kafka and notification verification](docs/phase-3.md) · [API guide](docs/phase-3-api-examples.md)
- [Phase 4 — Security, audit, reconciliation, resilience, outbox operations and rate limiting](docs/phase-4.md) · [API and Postman testing guide](docs/phase-4-api-examples.md)

There is no separate `docs/verification/` directory. The phase documents are the single source of truth for phase-specific verification; API guides provide the detailed request sequence and API-level test procedures.


Phase 4 Postman folders are feature-oriented: Authentication and JWT, Privileged role authentication, Role authorization (privileged token optional), Reconciliation, and Redis rate limiting. See [Phase 4 API testing guide](docs/phase-4-api-examples.md).
