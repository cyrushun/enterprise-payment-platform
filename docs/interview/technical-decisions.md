# Interview Talking Points

Use these as prompts for explaining decisions; update with implementation evidence as the project evolves.

## Why a modular monolith first?
It keeps the transfer and ledger transaction local and atomic while allowing clear module boundaries. Microservices are an option, not a default.

## Why PostgreSQL instead of Redis for balances?
Balances and ledger postings require durable ACID transactions, constraints, and row-level locks. Redis is not the financial source of truth.

## Why BigDecimal?
Binary floating-point cannot exactly represent many decimal fractions. Money needs explicit decimal precision, scale, and rounding rules.

## Why double-entry ledger?
It creates an auditable record where every posted journal balances. Corrections are compensating entries rather than destructive edits.

## Why idempotency?
Clients retry after timeouts and networks fail. A durable key plus request hash prevents duplicate financial effects and detects key reuse with a different payload.

## Why deterministic row locking?
Two opposite-direction transfers can deadlock if they lock accounts in inconsistent order. Locking account IDs in a consistent order reduces this risk.

## Why transactional outbox?
A database commit and Kafka publish are not one atomic transaction. Persisting event intent with business state avoids the dual-write gap; consumers still need deduplication.


## Why add CI/CD and immutable image promotion?
GitHub Actions verifies the commit before image publication. GHCR image digests identify the exact artifact promoted from staging to production; the production workflow requires a protected Environment approval. This reduces ambiguity from mutable tags and provides a traceable release record. It does not by itself provide zero-downtime deployment, database rollback or production certification.

## Why Docker Compose on Linux VMs rather than Kubernetes?
The portfolio's current modular-monolith footprint does not require Kubernetes to demonstrate repeatable deployment. Docker Compose keeps the deployment model understandable and consistent with local development. Separate VMs and immutable images provide a practical staging/production promotion path while leaving HA/orchestration as a future architecture decision.

See the [single Phase 5 guide](../phase-5.md) for implementation and verification status.
