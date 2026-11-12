# Phase 3 — Transactional Outbox, Kafka and Notifications

## 3.1 Objective

Publish transfer-completed events asynchronously without introducing a database/Kafka dual-write gap, consume events idempotently and persist account notifications.

## 3.2 Event lifecycle and delivery guarantees

1. `TransferService.create` persists the transfer, balance changes, ledger entries, idempotency result and outbox event in one PostgreSQL transaction.
2. The outbox scheduler claims eligible rows in bounded batches using PostgreSQL row locking (`FOR UPDATE SKIP LOCKED`) and a processing lease.
3. The publisher sends the event to the configured Kafka transfer topic.
4. A successful send marks the outbox row PUBLISHED. A failed send is retried with backoff and eventually transitions to DEAD after the configured maximum attempts.
5. The Kafka consumer records a processed-event marker and creates notifications in a database transaction.
6. Consumer failures are retried by Spring Kafka and then routed to the configured dead-letter topic (DLT).

Delivery from PostgreSQL outbox to Kafka is **at least once**, not exactly once. A crash after Kafka accepts an event but before the outbox row is marked PUBLISHED can cause a duplicate publication. Consumer deduplication is therefore required.

## 3.3 APIs

| Method | Endpoint | Purpose / access |
|---|---|---|
| GET | `/api/v1/outbox/events?page=0&size=20` | List outbox events; Phase 4 OPERATIONS/ADMIN |
| GET | `/api/v1/outbox/events/{eventId}` | Retrieve one outbox event; Phase 4 OPERATIONS/ADMIN |
| POST | `/api/v1/outbox/events/{eventId}/replay` | Replay a DEAD event; Phase 4 OPERATIONS/ADMIN |
| GET | `/api/v1/notifications?accountId={accountId}&page=0&size=20` | Retrieve notifications for an account; authenticated access/ownership rules apply in Phase 4 |
| POST | `/api/v1/operations/kafka-dlt/replay` | Republish a DLT payload; Phase 4 OPERATIONS/ADMIN |

The Phase 3 event flow and Phase 4 operational authorization are documented separately. Use `docs/phase-3-api-examples.md` for concrete requests.

## 3.4 Test preparation

```powershell
docker compose up -d
docker compose ps
mvn clean test
mvn spring-boot:run
```

Confirm Kafka is reachable at the broker address configured in `application.yml`. Confirm PostgreSQL is healthy and Flyway has applied V3.

## 3.5 Scenario A — Transfer creates an outbox event

1. Create a successful transfer using the Phase 2 procedure and a new idempotency key.
2. Record the transfer ID.
3. Query `outbox_events` for the corresponding transfer event.
4. Expected: exactly one outbox row is associated with the transfer and initially eligible for publication.
5. Allow the publisher scheduler to run.
6. Expected: the row transitions to PUBLISHED after Kafka acknowledges the send.

Database query:

```sql
SELECT id, aggregate_type, aggregate_id, event_type, status, attempts,
       created_at, published_at, last_error
FROM outbox_events
ORDER BY created_at DESC
LIMIT 50;
```

Use the actual V1/V3 column names in the migration if they differ.

## 3.6 Scenario B — Consumer and notification persistence

1. Allow the Kafka consumer to receive the transfer event.
2. Query `processed_events` and `notifications`.
3. Expected: one processed-event marker exists for the event, and the expected recipient notifications are persisted.
4. Allow or force duplicate delivery in an integration test.
5. Expected: the duplicate event does not create duplicate notifications because event processing is deduplicated and notification uniqueness is enforced.

```sql
SELECT event_id, event_type, consumer_name, processed_at
FROM processed_events
ORDER BY processed_at DESC
LIMIT 50;

SELECT id, event_id, recipient_account_id, title, message, created_at
FROM notifications
ORDER BY created_at DESC
LIMIT 50;
```

## 3.7 Scenario C — Kafka unavailable and recovery

1. Stop Kafka in a disposable local environment:

```powershell
docker compose stop kafka
```

2. Create a successful transfer while PostgreSQL remains healthy.
3. Expected: transfer and outbox row commit successfully; publisher cannot mark the event PUBLISHED while Kafka is unavailable.
4. Inspect outbox attempts, status and last error. Confirm retry is bounded and the row remains recoverable.
5. Restart Kafka:

```powershell
docker compose up -d kafka
```

6. Expected: publisher eventually delivers the event and marks the outbox row PUBLISHED.
7. Confirm the consumer processes the event and persists notifications.

## 3.8 Scenario D — Consumer retry and DLT

Use an integration test or controlled local fault injection to make the consumer fail for a known test event.

Expected:

- The configured listener retry policy is applied.
- After retries are exhausted, the message is routed to the configured DLT.
- The original event and financial transfer remain persisted.
- A later successful replay can be consumed without duplicate notification effects.

Do not inject failures into a production broker or real customer data.

## 3.9 Automated and regression verification

```powershell
mvn clean test
```

Run the Phase 1 and Phase 2 Postman collections. Verify account/transfer/ledger behavior remains unchanged when asynchronous notification processing is enabled.

## 3.10 Pass condition

Transfer and outbox persistence are atomic; Kafka publication is recoverable and at-least-once; consumer processing is idempotent; notifications are persisted once per intended event/recipient; Kafka outages do not roll back already committed financial transfers.

---

> **Current project context:** This document records the scope and verification details for its named phase. The current overall project status is maintained in the root [README](../README.md). Phase 5 observability, performance, deployment, CI/CD and current operational status are consolidated in the [single Phase 5 source of truth](phase-5.md).
