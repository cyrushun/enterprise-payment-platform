# Enterprise Payment Platform

A portfolio-grade, simulated banking and payment platform built to demonstrate senior-level Java engineering: transactional correctness, concurrency control, idempotency, double-entry accounting, event-driven integration, security, observability, and performance engineering.

> **Disclaimer:** This is an educational simulation. It is not intended to hold, transfer, or process real customer funds.

## Phase 0 scope

Phase 0 establishes the engineering foundation:

- Business scope, requirements, and non-functional requirements
- Modular-monolith architecture and module boundaries
- Architecture decision records (ADRs)
- Java 21 + Spring Boot 4 + Maven project skeleton
- PostgreSQL and Redis local infrastructure
- Initial Flyway schema
- OpenCode repository guidance
- Local setup and verification instructions

Kafka is intentionally deferred until the transactional core and the Transactional Outbox are implemented. This keeps the first milestone focused on correctness rather than infrastructure breadth.

## Technology baseline

| Area | Choice |
|---|---|
| Language | Java 21 LTS |
| Framework | Spring Boot 4.0.0 |
| Build | Maven |
| Persistence | Spring Data JPA / Hibernate |
| Database | PostgreSQL 17 |
| Schema migration | Flyway |
| Cache | Redis 7 |
| API | REST, JSON, OpenAPI (to be added with API phase) |
| Testing | JUnit 5, Mockito, Testcontainers |
| Local environment | Docker Desktop on Windows 11 |
| IDE | IntelliJ IDEA |
| AI coding assistant | OpenCode |

Versions are pinned in `pom.xml` and `docker-compose.yml` for reproducible setup. Review upgrade notes before changing major versions.

## Repository map

```text
enterprise-payment-platform/
├── AGENTS.md
├── opencode.json
├── README.md
├── pom.xml
├── docker-compose.yml
├── .env.example
├── .gitignore
├── docs/
│   ├── requirements/
│   ├── architecture/
│   │   └── decisions/
│   ├── database/
│   ├── api/
│   └── interview/
├── src/
│   ├── main/
│   │   ├── java/com/enterprise/payment/
│   │   └── resources/db/migration/
│   └── test/java/com/enterprise/payment/
└── deployment/monitoring/
```

## Quick start — Windows 11

### Prerequisites

1. Install a JDK 21 distribution (Temurin or Microsoft Build of OpenJDK).
2. Install IntelliJ IDEA.
3. Install Docker Desktop and enable the WSL 2 backend.
4. Install Git.
5. Optional: install OpenCode and configure your preferred model/provider.

Verify:

```powershell
java -version
mvn -version
docker version
docker compose version
```

Ensure `java` and Maven report Java 21.

### 1. Configure environment

Copy the example file:

```powershell
Copy-Item .env.example .env
```

The default credentials are for local development only. Do not reuse them outside a local machine.

### 2. Start infrastructure

From the repository root:

```powershell
docker compose up -d
docker compose ps
```

PostgreSQL is exposed on `localhost:5432`; Redis is exposed on `localhost:6379`.

### 3. Run the application

In IntelliJ IDEA:

1. Open the repository root as a Maven project.
2. Set Project SDK and Maven runner JDK to 21.
3. Wait for Maven dependency import.
4. Run `PaymentPlatformApplication`.

Or use PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

If Maven Wrapper has not yet been generated, use:

```powershell
mvn spring-boot:run
```

The application runs on `http://localhost:8080`.

### 4. Verify

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Expected status includes `UP`. Flyway applies the initial schema on application startup.

### 5. Inspect PostgreSQL

```powershell
docker compose exec postgres psql -U payment_app -d payment_platform
```

Useful SQL:

```sql
\dt
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

Exit with `\q`.

### 6. Stop infrastructure

```powershell
docker compose down
```

To remove local database data as well:

```powershell
docker compose down -v
```

**Warning:** `down -v` deletes the local PostgreSQL and Redis volumes.

## Configuration

Spring configuration is in `src/main/resources/application.yml`. Environment variables can override local defaults. See `.env.example`.

Do not commit `.env`, credentials, tokens, private keys, or production secrets.

## Database schema

The first migration creates the foundational entities:

- `customers`
- `accounts`
- `transfers`
- `ledger_transactions`
- `ledger_entries`
- `idempotency_records`
- `outbox_events`
- `audit_logs`

See `docs/database/database-design.md`.

Important accounting rule: each posted journal must balance (total debits equal total credits). A row-level `CHECK` constraint cannot enforce a sum across multiple rows. The application posting transaction and a later database-level deferred validation strategy must enforce this invariant. Never treat a transfer status alone as proof that the ledger is correct.

## Development workflow

1. Read `AGENTS.md` before using OpenCode.
2. Read relevant requirements and ADRs before implementing a feature.
3. Create or update an ADR when a material architecture decision changes.
4. Add tests with every business behavior.
5. Use Flyway for all schema changes; do not rely on Hibernate schema auto-update.
6. Never update or delete posted ledger entries. Corrections are represented by compensating entries.
7. Do not log secrets, authentication tokens, or full sensitive payment data.

## Current milestone and next steps

- [x] Phase 0: repository foundation, architecture, ADRs, initial schema, local infrastructure
- [ ] Phase 1: customer, account, and ledger domain model and use cases
- [ ] Phase 2: transfers, concurrency, idempotency, and failure handling
- [ ] Phase 3: transactional outbox, Kafka, and notifications
- [ ] Phase 4: security, audit hardening, reconciliation, and resilience
- [ ] Phase 5: load testing, observability, and deployment

See `docs/requirements/business-requirements.md` and `docs/architecture/system-architecture.md`.
