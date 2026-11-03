# ADR-0004: Transactional Outbox for Event Publication

- Status: Accepted
- Date: 2026-11-01

## Context

Publishing a Kafka event after committing a database transaction can lose the event if the process crashes between the commit and publish. Publishing before commit can expose an event for a transaction that later rolls back.

## Decision

Write an outbox row in the same PostgreSQL transaction as the business state. A separate publisher sends it to Kafka and records publication progress.

## Consequences

Positive:
- Business state and event intent commit atomically.
- Events remain durable while Kafka is unavailable.

Negative:
- Publisher, retry, ordering, and cleanup logic are required.
- Delivery is at-least-once; consumers must deduplicate.
- Kafka is deferred until the outbox use case is implemented and tested.
