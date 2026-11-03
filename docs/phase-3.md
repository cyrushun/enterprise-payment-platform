# Phase 3 — Transactional Outbox, Kafka and Notifications

## Scope
This implementation adds the Kafka publisher, Kafka consumer, notification persistence, retry handling, and read-only development APIs. The `outbox_events` table was already created by V1, so V3 extends that existing table with `ALTER TABLE`; V1 and V2 remain unchanged. Existing Phase 1/2 endpoints and persisted data are retained.

## Event flow
1. `TransferService.create` persists transfer, account balance updates, ledger entries, idempotency record, and `TransferCompleted` outbox row in one PostgreSQL transaction.
2. `OutboxScheduler` claims rows using `FOR UPDATE SKIP LOCKED`, sets a 60-second processing lease, and commits the claim.
3. `OutboxPublisher` publishes JSON to `payment.transfer-events`. On success it marks the row PUBLISHED. On failure it schedules exponential retry; after the configured attempt limit it marks DEAD.
4. `TransferEventConsumer` consumes the event. `NotificationService` stores one processed-event marker and two account notifications in one DB transaction.
5. Spring Kafka retries consumer failures three times and then routes the record to `payment.transfer-events.DLT`.

## Important delivery semantics
- Transfer and outbox insert are atomic in PostgreSQL.
- Outbox-to-Kafka delivery is at-least-once. A process can crash after Kafka accepts an event but before the outbox row is marked PUBLISHED; a later retry can publish it again.
- Consumer deduplication uses `processed_events.event_id` and notification uniqueness `(event_id, recipient_account_id)`.
- The current sample consumer is a single logical consumer group. For production, add stronger concurrent duplicate handling, monitoring/alerting for DEAD events and DLT, retention policy, schema versioning, and operational replay tooling.
- Development monitoring endpoints are not authenticated yet. Do not expose them to an untrusted network.

## Run
```bash
docker compose up -d
docker compose ps
mvn clean test
mvn spring-boot:run
```
Spring Boot applies V3 automatically. V3 adds the Phase 3 publisher columns to the V1 `outbox_events` table and creates `processed_events` and `notifications`; it does not recreate `outbox_events`. Check `flyway_schema_history`. Kafka is exposed at `localhost:9092`.

## APIs
- `GET /api/v1/outbox/events?page=0&size=20`
- `GET /api/v1/outbox/events/{eventId}`
- `GET /api/v1/notifications?accountId={accountUuid}&page=0&size=20`

## SQL checks
```sql
SELECT id, event_type, status, attempts, created_at, published_at, last_error
FROM outbox_events ORDER BY created_at DESC;

SELECT event_id, event_type, consumer_name, processed_at
FROM processed_events ORDER BY processed_at DESC;

SELECT id, event_id, recipient_account_id, title, message, created_at
FROM notifications ORDER BY created_at DESC;
```

## Failure test
Stop Kafka while leaving PostgreSQL up, submit a new successful transfer, and verify its outbox row remains PENDING/RETRY (the publisher will retry and eventually mark DEAD if Kafka stays down). Restart Kafka; a non-DEAD row will be retried automatically. A DEAD row currently requires operator intervention; do not manually change production records without an audited replay procedure.
