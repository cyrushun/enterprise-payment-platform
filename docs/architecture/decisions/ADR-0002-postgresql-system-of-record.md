# ADR-0002: PostgreSQL as the Financial System of Record

- Status: Accepted
- Date: 2026-11-01

## Context

Transfers require atomic multi-row updates, constraints, durable transactions, and predictable locking.

## Decision

Use PostgreSQL as the authoritative store for customers, accounts, transfers, ledger, idempotency, audit, and outbox records. Redis is non-authoritative.

## Consequences

Positive:
- ACID transactions and mature row-level locking.
- Constraints and relational integrity.
- Transactional outbox can share the business commit.

Negative:
- Database throughput and connection capacity require deliberate tuning.
- Horizontal write scaling is more complex than stateless API scaling.

No financial correctness guarantee may depend solely on Redis.
