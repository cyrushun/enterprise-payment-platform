# Repository Instructions for OpenCode

## Role

Act as a senior Java engineer and pragmatic software architect. Help build a secure, testable, maintainable simulated payment platform. Prefer correctness and clear reasoning over clever abstractions.

## Mandatory project constraints

- Java 21.
- Spring Boot 4.x; current baseline is pinned in `pom.xml`.
- Maven build.
- PostgreSQL is the system of record.
- Flyway owns all schema changes.
- Redis is a cache/ephemeral coordination component, never the financial source of truth.
- Start as a modular monolith. Do not create microservices without an approved ADR.
- Use `BigDecimal` for money. Never use `float` or `double` for monetary values.
- Use `UUID` identifiers unless an approved design requires otherwise.
- Use UTC `Instant` for timestamps.
- Use explicit currency codes and do not silently mix currencies.
- Use constructor injection; avoid field injection.
- Keep domain rules out of controllers and persistence entities.
- Avoid generic repository/service layers that obscure use cases.

## Financial correctness and security

- A transfer must be atomic: both sides of the financial posting commit or neither does.
- A posted ledger journal must balance: sum(debits) = sum(credits).
- Posted ledger entries are immutable. Correct mistakes using compensating entries.
- Protect against concurrent spending using database transactions and a documented locking strategy.
- Idempotency must be scoped and persisted; do not rely on an in-memory map.
- Never trust a client-supplied account owner or tenant identity.
- Validate account status, currency, amount, ownership/authorization, and available funds.
- Do not log credentials, access tokens, or sensitive payment information.
- Use parameterized SQL and validated input.
- Do not claim that a database transaction spans Kafka or another remote service.

## Architecture boundaries

Use package-by-feature modules:

- `customer`
- `account`
- `payment`
- `ledger`
- `identity`
- `notification`
- `audit`
- `reconciliation`
- `shared`

Each feature should separate API/application/domain/infrastructure concerns when complexity warrants it. Modules communicate through application interfaces or domain events, not by reaching into another module's repositories.

## Coding standards

- Prefer small, cohesive classes and explicit names.
- Prefer immutable request/response DTOs (records where suitable).
- Use `Optional` only for return values where absence is expected; do not use it for entity fields or method parameters.
- Avoid broad catch blocks and swallowed exceptions.
- Define stable error codes and meaningful HTTP statuses.
- Add Javadoc for non-obvious financial invariants and concurrency decisions.
- Keep configuration externalized.
- Do not introduce Lombok unless explicitly requested.
- Do not add dependencies without explaining why they are needed.

## Testing requirements

- Unit-test domain rules and use cases.
- Integration-test persistence and transaction behavior against PostgreSQL using Testcontainers.
- Test duplicate idempotency keys, concurrent transfers, rollback behavior, insufficient funds, currency mismatch, and balanced ledger postings.
- Do not use H2 as a substitute for PostgreSQL tests of locking, isolation, SQL, or constraints.
- Tests must be deterministic and must not depend on external services.

## Database rules

- Add schema changes as new Flyway migrations; never edit a migration that has already been applied/shared.
- Avoid destructive migrations.
- Add foreign keys, unique constraints, checks, and indexes deliberately.
- Document any invariant that cannot be enforced by a simple database constraint.
- Never update/delete posted ledger entries.

## Before completing a task

1. Summarize the design and relevant trade-offs.
2. List files changed.
3. Run or state the exact build/test commands.
4. Report test results honestly; never claim tests passed if they were not run.
5. Identify unresolved risks and follow-up work.


## Phase 4.7 rate limiting
- Redis is actively used for atomic, expiring API rate-limit counters.
- PostgreSQL remains the sole financial source of truth. Never use Redis for balances, ledger posting, transfer locking, or idempotency correctness.
- Rate-limit behavior and Redis outage policy are configured under `app.rate-limit`.


## Phase 5 documentation and delivery rules
- `docs/phase-5.md` is the single source of truth for Phase 5.1–5.4. Do not create separate Phase 5 runbooks or duplicate setup instructions in other Markdown files. Update the canonical document and README whenever Phase 5 behavior changes.
- Current status: Phase 5.1 and 5.2 are user-reported complete; Phase 5.3 deployment verification is user-verified; Phase 5.4 CI/CD implementation is present but GitHub Actions and Linux VM runtime verification are pending. Never claim remote workflows or VM deployment passed until evidence is recorded.
- CI must run Maven verification, Testcontainers integration tests, security analysis and a Docker build. Use immutable GHCR image digests for staging/production promotion.
- Staging and production must use separate Linux VMs, database volumes, `.env` files, JWT key pairs and credentials. Never commit `.env`, `.secrets/`, private keys, backups or deployment state.
- Never use `docker compose down -v` as a troubleshooting shortcut; it destroys persistent volumes.
- Application image rollback does not reverse Flyway migrations. Migrations must use backward-compatible expand/contract practices and database restore must be separately approved and rehearsed.
