# Phase 3 — API Examples

Base URL: `http://localhost:8080`. The examples assume the local Docker Compose stack is running.

> Phase 3 endpoints are protected by the Phase 4 stateless bearer-token security configuration. Obtain an access token using `POST /api/v1/auth/login` and send `Authorization: Bearer <accessToken>`.

## 1. List outbox events

```http
GET /api/v1/outbox/events?page=0&size=20 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <accessToken>
```

Role: `OPERATIONS` or `ADMIN`.

## 2. Get an outbox event

```http
GET /api/v1/outbox/events/{eventId} HTTP/1.1
Host: localhost:8080
Authorization: Bearer <accessToken>
```

Replace `{eventId}` with the event UUID.

## 3. List notifications for an account

```http
GET /api/v1/notifications?accountId={accountUuid}&page=0&size=20 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <accessToken>
```

Use an account UUID created in Phase 1. Check the current controller/service implementation for the exact response fields and pagination envelope.

## 4. Verify event and notification persistence

```sql
SELECT id, event_type, status, attempts, created_at, published_at, last_error
FROM outbox_events ORDER BY created_at DESC;

SELECT event_id, event_type, consumer_name, processed_at
FROM processed_events ORDER BY processed_at DESC;

SELECT id, event_id, recipient_account_id, title, message, created_at
FROM notifications ORDER BY created_at DESC;
```

See [Phase 3 implementation](phase-3.md) for event flow, delivery semantics, and Kafka failure testing.
