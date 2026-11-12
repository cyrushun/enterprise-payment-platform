# Enterprise Payment Platform

A portfolio-grade simulated banking and payment platform demonstrating senior-level Java engineering: transactional correctness, concurrency control, idempotency, double-entry accounting, transactional outbox/event-driven integration, identity/security, auditability, reconciliation and operational resilience.

> **Disclaimer:** Educational simulation only. It is not intended to hold, transfer or process real customer funds.

## Project status

| Phase | Scope | Current status |
|---|---|---|
| 0 | Foundation, architecture, requirements, schema and local infrastructure | Completed |
| 1 | Customers, accounts and balance APIs | Completed |
| 2 | Transfers, double-entry ledger, concurrency and idempotency | Completed |
| 3 | Transactional outbox, Kafka publisher/consumer and notifications | Completed |
| 4.1–4.7 | JWT/RBAC, audit, reconciliation, dependency resilience, outbox operations, API error handling and Redis rate limiting | Completed |
| 5.1 | Observability | User-reported complete |
| 5.2 | Performance and load testing | User-reported complete; retain actual run evidence and measured results |
| 5.3 | Containerization and deployment readiness | User-verified complete |
| 5.4 | CI/CD and production-readiness baseline | Implemented; GitHub Actions and Linux VM runtime verification pending |

A phase being implemented or completed does not mean the system is certified for real-money, regulated or high-availability production use. See the relevant phase guide for verification boundaries.

## Technology baseline

| Area | Technology |
|---|---|
| Language/runtime | Java 21 LTS |
| Framework | Spring Boot 4.0.0 |
| Build | Maven |
| Persistence | Spring Data JPA / Hibernate and JDBC where appropriate |
| System of record | PostgreSQL 17 / Flyway |
| Messaging | Apache Kafka (KRaft in Compose) |
| Ephemeral coordination | Redis 7 |
| Security | Spring Security, RSA-signed JWT, BCrypt |
| Testing | JUnit 5, Mockito, Testcontainers |
| Observability | Actuator, Micrometer, Prometheus, Grafana, OpenTelemetry/Jaeger |
| Performance | k6 (native Windows installation) |
| Delivery | GitHub Actions, GHCR, Docker Compose on Linux VMs |

Versions are pinned in `pom.xml` and `docker-compose.yml`.

## Repository layout

```text
enterprise-payment-platform/
├── .github/workflows/          # CI, image publishing, production deployment
├── src/main/java/              # Spring Boot application
├── src/main/resources/
│   ├── application.yml
│   ├── application-docker.yml
│   └── db/migration/           # Append-only Flyway migrations V1–V7
├── src/test/java/              # Unit and integration tests
├── docs/
│   ├── phase-0.md ... phase-4.md
│   ├── phase-4-api-examples.md
│   ├── phase-5.md              # Single source of truth for all Phase 5 work
│   ├── architecture/
│   ├── database/
│   ├── api/
│   ├── requirements/
│   └── interview/
├── load-tests/                 # k6 workloads and SQL diagnostics
├── scripts/                    # PowerShell and Linux deployment scripts
├── observability/              # Prometheus/Grafana provisioning
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── AGENTS.md
└── README.md
```

## Prerequisites

- JDK 21
- Maven 3.9+
- Docker Desktop with Docker Compose v2
- Git
- Optional: IntelliJ IDEA and OpenCode

Verify on Windows PowerShell:

```powershell
java -version
mvn -version
docker version
docker compose version
```

## Quick start — Windows PowerShell

1. Copy `.env.example` to `.env` and replace placeholders with local-only values. If PostgreSQL has already initialized a persistent volume, the role password stored in PostgreSQL must match `POSTGRES_PASSWORD`; changing `.env` alone does not rotate an existing database role password.
2. Generate local observability configuration and the Prometheus metrics password file:

```powershell
.\scripts\setup-observability.ps1
```

3. Start infrastructure dependencies:

```powershell
docker compose config --quiet
docker compose up -d
docker compose ps
```

4. Run the automated tests:

```powershell
mvn clean verify
```

5. Start the API on the Windows host with `.env` values loaded:

```powershell
.\scripts\run-local-api.ps1
```

6. Check health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

To run the API in Docker instead, provision local JWT files under `.secrets/`, then use `scripts/deploy-local-stack.ps1` and `scripts/verify-deployment.ps1`. For the containerized API, the datasource host is the Compose service `postgres`, not `localhost`. Do not run `docker compose down -v` as a troubleshooting step; it removes persistent data volumes.

## Database migration policy

Flyway migrations are append-only. The current sequence is V1–V7: foundational schema; transfer integrity; outbox/notifications; identity and reconciliation; operations hardening; local operations/admin test fixture; observability trace/correlation context.

**Never edit a migration already applied/shared in an environment.** Add a new migration with the next version. Hibernate uses `ddl-auto: validate`; it does not own schema changes. Verify history with:

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## Security and secrets

The checked-in RSA key pair is for local development only. Use unique staging and production keys provisioned securely. Never commit `.env`, `.secrets/`, private keys, access/refresh tokens, backups or real customer data. Public registration grants only `CUSTOMER`. PostgreSQL remains the financial system of record; Redis is not authoritative for balances, ledger posting or idempotency.

## Documentation map

- [Phase 0 — Foundation](docs/phase-0.md)
- [Phase 1 — Customers and Accounts](docs/phase-1.md) · [API examples](docs/phase-1-api-examples.md)
- [Phase 2 — Transfers and Ledger](docs/phase-2.md) · [API examples](docs/phase-2-api-examples.md)
- [Phase 3 — Outbox, Kafka and Notifications](docs/phase-3.md) · [API examples](docs/phase-3-api-examples.md)
- [Phase 4 — Security, Audit, Reconciliation, Resilience and Rate Limiting](docs/phase-4.md) · [API/Postman guide](docs/phase-4-api-examples.md)
- **[Phase 5 — Single source of truth: Observability, Performance, Deployment and CI/CD](docs/phase-5.md)**
- [API specification](docs/api/api-specification.md)
- [System architecture](docs/architecture/system-architecture.md) · [Deployment architecture](docs/architecture/deployment-diagram.md)
- [Database design](docs/database/database-design.md)

### Phase 5 documentation rule

`docs/phase-5.md` is the sole authoritative Phase 5 document for 5.1, 5.2, 5.3 and 5.4. It contains implementation scope, configuration, commands, verification, security boundaries, deployment, backup/restore, release and rollback guidance. Do not create separate Phase 5 runbooks or maintain duplicate instructions elsewhere. Other documents should link to the canonical guide.

## Development rules

1. Read `AGENTS.md` and the relevant phase document before changing code.
2. Update the canonical phase document and this README when implementation scope/status changes.
3. Add tests for new business/security behavior.
4. Add new Flyway migrations; never rewrite applied migrations.
5. Preserve existing API contracts unless a documented, versioned change is intended.
6. Never mutate posted ledger entries; use compensating entries.
7. Do not log credentials, tokens or sensitive payment data.
8. Record material architecture changes as ADRs.
9. Report build/test results honestly; distinguish static checks from tests actually executed.

## Postman collections

Import the Phase 1, Phase 2, Phase 3 and Phase 4 collections together with `Enterprise-Payment-Platform-Local.postman_environment.json`. Phase 4 role-specific token variables keep CUSTOMER, OPERATIONS and ADMIN credentials separate. Phase 5.1 observability requests are included in `Enterprise-Payment-Platform-Phase5.1.postman_collection.json`. Run collections in dependency order and use dedicated test accounts.
