# Design Trade-offs to Be Ready to Defend

1. **Modular monolith vs microservices:** local ACID consistency and simpler operations versus independent deployment and scaling.
2. **Pessimistic vs optimistic locking:** row locks are straightforward for hot balance rows; optimistic versioning can work for lower contention but needs conflict retry handling.
3. **Balance projection vs ledger-derived reads:** fast balance reads versus additional reconciliation and consistency responsibilities.
4. **Outbox at-least-once vs distributed exactly-once claims:** durable intent and idempotent consumers are practical; end-to-end exactly-once business effects require application-level design.
5. **Redis cache vs direct database reads:** lower read load versus invalidation and stale-data risk. Never use a cache value as the final authority for funds availability.
6. **Schema enforcement vs application invariants:** use both where practical; cross-row journal balancing needs more than a simple CHECK constraint.
