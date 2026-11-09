# Phase 0 — Project Foundation

## Objectives

Establish the engineering baseline before implementing customer, account and payment behavior.

## Delivered foundation

- Java 21 and Spring Boot 4 Maven project.
- Modular-monolith package structure under `com.enterprise.payment`.
- PostgreSQL and Redis local infrastructure through Docker Compose.
- Flyway-managed initial schema (V1).
- Requirements, non-functional requirements, system/component/deployment architecture and ADRs.
- Repository guidance for consistent implementation and testing.

## Verification

Run `java -version`, `mvn -version`, `docker compose up -d`, `docker compose ps`, and `mvn clean test`. Verify PostgreSQL connectivity and that Flyway records V1 in `flyway_schema_history`.

## Related documents

See `docs/requirements/`, `docs/architecture/`, `docs/database/database-design.md`, and the root `README.md` for setup instructions.
