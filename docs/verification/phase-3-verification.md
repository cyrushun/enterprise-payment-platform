# Phase 3 — Transactional outbox and notification verification

## Prerequisites
Complete Phase 2 and have a valid transfer scenario. Kafka and PostgreSQL must be running.

## Procedure
1. Run `mvn clean test`; import `Enterprise-Payment-Platform-Phase3.postman_collection.json`.
2. Complete a Phase 2 transfer. Confirm the outbox event row is inserted atomically with transfer/journal changes.
3. Wait for publisher/consumer; inspect event status and notification endpoint. Verify one notification exists.
4. Redeliver the same event in a test fixture; verify processed-event idempotency prevents a second notification.
5. Stop Kafka (`docker compose stop kafka`), create a transfer, and confirm outbox row remains persisted and not PUBLISHED.
6. Restart Kafka (`docker compose up -d kafka`); verify retries eventually publish and consumer processes event.
7. Force repeated failures only in disposable environment; verify attempts increase, next retry is scheduled, and retry exhaustion reaches DEAD.
8. Verify publisher claims are bounded and concurrent workers do not process the same row simultaneously.

## Acceptance
Transfer/outbox atomicity, retry, eventual publication, duplicate-consumer protection and bounded terminal failure behavior all hold.

## Evidence
Outbox row snapshots, notification result, broker logs, Postman run and automated test output.
