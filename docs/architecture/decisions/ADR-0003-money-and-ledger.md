# ADR-0003: Decimal Money and Double-Entry Ledger

- Status: Accepted
- Date: 2026-11-01

## Context

Binary floating-point types cannot represent many decimal fractions exactly. A payment platform needs auditable financial history and balanced postings.

## Decision

Use Java `BigDecimal` and PostgreSQL `NUMERIC(19,4)` for monetary values. Store currency explicitly. Represent posted financial activity as immutable double-entry journals.

## Consequences

Positive:
- Decimal arithmetic avoids binary floating-point representation errors.
- Ledger provides an auditable record and reconciliation basis.
- Corrections can be represented as compensating postings.

Negative:
- Scale, rounding, and currency precision policies must be explicit.
- A cross-row balancing invariant needs application validation and potentially deferred database enforcement; a simple row check is insufficient.
