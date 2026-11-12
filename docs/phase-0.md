# Phase 0 — Project Foundation

## 0.1 Objective

Establish a reproducible Java/Spring Boot project, local infrastructure, database migration baseline, architecture documentation and automated-test entry point before implementing payment capabilities.

## 0.2 Delivered baseline

- Java 21 and Spring Boot 4 Maven application.
- Modular-monolith package structure under `com.enterprise.payment`.
- PostgreSQL and Redis local services through Docker Compose; Kafka is introduced for the Phase 3 event flow.
- Flyway-managed database schema beginning with V1.
- Requirements, architecture diagrams, database design and architecture decision records.
- Maven test lifecycle and repository-level implementation guidance.

## 0.3 Environment and startup verification

Run these commands from the repository root in PowerShell:

```powershell
java -version
mvn -version
docker version
docker compose config
docker compose up -d
docker compose ps
```

Expected observations:

1. Java reports version 21.
2. Maven is available and uses the expected Java runtime.
3. `docker compose config` completes without YAML or interpolation errors.
4. Required containers reach their healthy/running state. Check individual health status rather than relying only on container existence.

Verify PostgreSQL connectivity:

```powershell
docker compose exec postgres pg_isready -U payment -d payment_platform
```

Expected: PostgreSQL reports that it is accepting connections. Use the actual service/database/user names from the checked-in `docker-compose.yml` if they differ.

## 0.4 Build and migration verification

```powershell
mvn clean test
mvn spring-boot:run
```

After application startup, inspect Flyway history:

```sql
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

Expected:

- V1 is present and successful on a new database.
- No failed migration row exists.
- The tables defined by V1 exist, including the customer/account, ledger, audit and outbox foundation tables documented in `docs/database/database-design.md`.

For a clean-database test, use a disposable local database. Do not drop or reset a database containing data you need to retain.

## 0.5 Configuration and infrastructure checks

- Confirm application datasource settings match the PostgreSQL service in Docker Compose.
- Confirm Redis host, port and timeout settings match the Redis service.
- Confirm exposed ports do not conflict with other local services.
- Confirm secrets and local overrides are supplied through environment variables or local-only configuration; do not commit production credentials.

## 0.6 Failure scenarios

| Scenario | Expected result |
|---|---|
| Docker daemon is stopped | Docker commands fail clearly; application dependencies are not assumed to be available |
| PostgreSQL is stopped before startup | Application startup/readiness reflects the database connection failure according to configured behavior |
| Invalid Compose interpolation | `docker compose config` identifies the invalid configuration |
| Flyway migration fails | Application startup fails rather than silently running against a partial schema |
| Maven test fails | Build exits non-zero and the failure is investigated before proceeding |

## 0.7 Pass condition

The project builds, the local services start, the application connects to PostgreSQL, Flyway applies V1 successfully on a clean database, and `mvn clean test` completes successfully.

## Related technical references

- `docs/requirements/`
- `docs/architecture/`
- `docs/database/database-design.md`
- `docs/api/api-specification.md`
- `README.md`

---

> **Current project context:** This document records the scope and verification details for its named phase. The current overall project status is maintained in the root [README](../README.md). Phase 5 observability, performance, deployment, CI/CD and current operational status are consolidated in the [single Phase 5 source of truth](phase-5.md).
