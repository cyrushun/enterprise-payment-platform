# ADR-0001: Start with a Modular Monolith

- Status: Accepted
- Date: 2026-11-01

## Context

The portfolio must demonstrate architecture judgment as well as distributed systems knowledge. Starting with many services would introduce network and operational failure modes before core financial invariants are proven.

## Decision

Start with one Spring Boot deployable organized into package-by-feature modules with explicit boundaries and application interfaces.

## Consequences

Positive:
- One database transaction can atomically update transfer, account projections, ledger, idempotency, and outbox.
- Easier local setup, testing, debugging, and delivery.
- Clear extraction seams remain possible.

Negative:
- Requires discipline to prevent module coupling.
- Independent scaling/deployment is not available initially.

Revisit when measurable scaling, team ownership, deployment independence, or isolation requirements justify extraction.
