# Phase 0 — Foundation verification

## Prerequisites
- JDK 21, Maven 3.9+, Docker Desktop with Compose.
- Run from project root; preserve existing `.env` and use `.env.example` only for first-time setup.

## Procedure
1. `java -version`, `mvn -version`, `docker version`, `docker compose version`.
2. `docker compose up -d`; `docker compose ps`; inspect `docker compose logs postgres`, `kafka`, `redis`.
3. `mvn clean test` and retain the complete output.
4. `mvn spring-boot:run`; verify `/actuator/health`, `/actuator/health/readiness`, `/actuator/health/liveness` return 200.
5. Query `flyway_schema_history`; confirm V1 success on a clean database.
6. Confirm Hibernate uses `ddl-auto: validate`; no Hibernate auto-DDL should create or mutate schema.
7. Review `docs/architecture`, `docs/database`, `docs/requirements`, ADRs and Compose topology against actual services/ports.
8. Verify secrets are externalized; `.env`, private keys and credentials are not committed as production secrets.

## Acceptance
- All required containers are healthy, application starts, migration succeeds, schema validation passes and automated tests pass.
- Database tables exist because Flyway ran, not because Hibernate silently created them.
- Architecture and deployment documentation matches actual runtime topology.

## Evidence
Maven log, `docker compose ps`, health responses, Flyway history and sanitized startup logs.
