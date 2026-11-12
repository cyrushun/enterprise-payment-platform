# Phase 3 — Outbox, Kafka and Notification Testing Guide

Base URL: `http://localhost:8080`.

## Prerequisites

- PostgreSQL and Kafka are running.
- Flyway has applied V3.
- A successful Phase 2 transfer can be created.
- After Phase 4 is applied, operational endpoints require an OPERATIONS or ADMIN bearer token. Notification access requires an authenticated identity and the applicable account ownership rule.

## 1. Generate a transfer event

Create a successful transfer using the Phase 2 procedure and a new idempotency key. Record the transfer ID.

Expected: transfer, ledger entries, idempotency result and outbox event are committed in one PostgreSQL transaction.

## 2. Verify outbox persistence

```sql
SELECT id, aggregate_type, aggregate_id, event_type, status, attempts,
       created_at, published_at, last_error
FROM outbox_events
ORDER BY created_at DESC
LIMIT 50;
```

Expected: one outbox event is associated with the transfer. After successful Kafka publication, its status becomes PUBLISHED and `published_at` is populated.

## 3. List and retrieve outbox events (Phase 4 operational access)

```http
GET /api/v1/outbox/events?page=0&size=20
Authorization: Bearer {{operationsToken}}
```

Expected: HTTP `200 OK`.

```http
GET /api/v1/outbox/events/{{outboxEventId}}
Authorization: Bearer {{operationsToken}}
```

Expected: HTTP `200 OK` with the selected event.

## 4. Verify consumer processing and notifications

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

Expected: a processed marker exists and notifications are persisted for the intended recipients. Duplicate delivery must not create duplicate notification effects.

## 5. Kafka outage and recovery

1. Stop Kafka in a disposable local environment: `docker compose stop kafka`.
2. Create another successful transfer.
3. Expected: transfer and outbox row commit; publication does not become PUBLISHED while the broker is unavailable.
4. Restart Kafka: `docker compose up -d kafka`.
5. Expected: publisher retries and eventually marks the event PUBLISHED; consumer processes it.
6. Confirm notifications are persisted once.

## 6. Consumer retry and DLT

Use an integration test or controlled local fault injection to make the consumer fail for a test event.

Expected:

- Configured listener retries occur.
- Exhausted retries route the message to the configured DLT.
- The original transfer/outbox record remains persisted.
- Re-delivery/replay does not duplicate notifications.

## 7. Notification API

```http
GET /api/v1/notifications?accountId={{accountId}}&page=0&size=20
Authorization: Bearer {{customerToken}}
```

Expected: HTTP `200 OK` for an account the authenticated customer is permitted to access. A cross-customer account request must be denied according to the ownership policy.

## Pass condition

Outbox and transfer are atomically persisted, Kafka delivery recovers after outages, consumer processing is idempotent, and notification records are persisted without duplicates.

---

> **Current project context:** This document records the scope and verification details for its named phase. The current overall project status is maintained in the root [README](../README.md). Phase 5 observability, performance, deployment, CI/CD and current operational status are consolidated in the [single Phase 5 source of truth](phase-5.md).
