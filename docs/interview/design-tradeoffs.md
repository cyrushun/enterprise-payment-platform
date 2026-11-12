# Design Trade-offs to Be Ready to Defend

1. **Modular monolith vs microservices:** local ACID consistency and simpler operations versus independent deployment and scaling.
2. **Pessimistic vs optimistic locking:** row locks are straightforward for hot balance rows; optimistic versioning can work for lower contention but needs conflict retry handling.
3. **Balance projection vs ledger-derived reads:** fast balance reads versus additional reconciliation and consistency responsibilities.
4. **Outbox at-least-once vs distributed exactly-once claims:** durable intent and idempotent consumers are practical; end-to-end exactly-once business effects require application-level design.
5. **Redis cache vs direct database reads:** lower read load versus invalidation and stale-data risk. Never use a cache value as the final authority for funds availability.
6. **Schema enforcement vs application invariants:** use both where practical; cross-row journal balancing needs more than a simple CHECK constraint.

7. **Docker Compose vs Kubernetes:** Compose is proportionate for a single modular-monolith deployable and portfolio Linux VM; Kubernetes adds scheduling/orchestration complexity that is not justified by current requirements.
8. **Image tag vs digest:** human-friendly tags aid discovery but are mutable; deployment by immutable digest ensures staging and production use the same artifact.
9. **Application rollback vs database rollback:** an image can be reverted, but Flyway schema changes and external side effects are not automatically reversible. Use expand/contract migrations and separately rehearsed database recovery.
10. **Hosted vs self-hosted deployment runner:** GitHub-hosted runners simplify CI; production SSH ingress should be tightly controlled, with self-hosted runners/private overlay considered for hardened deployments.
