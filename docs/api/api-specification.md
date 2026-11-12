# API Specification — Implemented API Baseline

This document describes the API baseline implemented through Phase 4. Endpoint availability and role requirements are summarized below; use the corresponding phase guide and Postman collection as the executable verification reference. Payment initiation/refund endpoints remain future scope unless implemented by a later change.

## Endpoint groups

| Method | Path | Phase |
|---|---|---|
| POST | `/api/v1/customers` | 1 |
| GET | `/api/v1/customers/{customerId}` | 1 |
| POST | `/api/v1/accounts` | 1 |
| GET | `/api/v1/accounts/{accountId}` | 1 |
| GET | `/api/v1/accounts/{accountId}/balance` | 1 |
| POST | `/api/v1/transfers` | 2 |
| GET | `/api/v1/transfers/{transferId}` | 2 |
| GET | `/api/v1/accounts/{accountId}/transactions` | 2 |
| POST | `/api/v1/payments` | Later |
| POST | `/api/v1/payments/{paymentId}/refunds` | Later |
| GET | `/api/v1/audit-logs` | 4 |
| POST | `/api/v1/reconciliation/run` | 4 |

## Transfer request example

`POST /api/v1/transfers`

Required header: `Idempotency-Key: <opaque-client-generated-key>`

```json
{
  "fromAccountId": "UUID",
  "toAccountId": "UUID",
  "amount": "500.00",
  "currency": "MYR",
  "reference": "Invoice payment"
}
```

Represent money as a JSON decimal string to avoid client-side binary floating-point conversion. The server parses and validates it as `BigDecimal`.

## Transfer response example

```json
{
  "transferId": "UUID",
  "status": "COMPLETED",
  "amount": "500.00",
  "currency": "MYR",
  "createdAt": "2026-11-01T10:15:30Z"
}
```

The request/response shape is illustrative; use the implemented DTOs and Postman collections as the current contract.

## Phase 4 authentication endpoints

The implemented auth contract and request/response examples are documented in [Phase 4 API Examples](../phase-4-api-examples.md). Endpoints include `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/auth/me`, `POST /api/v1/auth/refresh`, and `POST /api/v1/auth/logout`. Use bearer access tokens for protected APIs. Public registration grants CUSTOMER only.

## Authorization route policy

The current security configuration applies ADMIN to `/api/v1/admin/**` and OPERATIONS or ADMIN to `/api/v1/audit-logs/**`, `/api/v1/reconciliation/**`, and `/api/v1/outbox/**`. These route policies do not guarantee that each controller/API is implemented. Check controller mapping and the phase verification checklist before treating a route as available.

## Phase 4.2–4.6 operations endpoints

The following operational endpoints require `ROLE_OPERATIONS` or `ROLE_ADMIN` (unless deployment overrides route policy):

- `GET /api/v1/audit-logs` — pageable audit log listing.
- `GET /api/v1/audit-logs/{id}` — retrieve one audit record.
- `GET /api/v1/reconciliation/runs` — recent reconciliation runs.
- `GET /api/v1/reconciliation/runs/{runId}/discrepancies` — persisted discrepancy detail.
- `POST /api/v1/reconciliation/run` — trigger a reconciliation scan.
- `GET /api/v1/outbox/events` and `GET /api/v1/outbox/events/{id}` — inspect outbox state.
- `POST /api/v1/outbox/events/{id}/replay` — replay a DEAD event after operational review.

Reconciliation is read-only against account/ledger balances. It persists discrepancy records and does not auto-adjust money. Outbox replay is permitted only for DEAD rows. Use the Phase 4 Postman collection and verification guide with an OPERATIONS/ADMIN token for privileged endpoints.

- `POST /api/v1/operations/kafka-dlt/replay` — re-publish a DLT payload to the transfer topic (OPERATIONS/ADMIN). Body: `{ "eventKey": "...", "payload": "<original JSON string>" }`. This is a controlled replay, not a delete/ack of the original Kafka DLT record.


## Standard error response

Controller errors use the shared `ApiError` shape: `code`, `message`, `timestamp`, and `details`. Expected mappings include `400 VALIDATION_ERROR` / `MALFORMED_REQUEST`, `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `404` resource-specific not-found codes, `409 RESOURCE_CONFLICT` / `INVALID_STATE`, `422` domain-rule codes such as `INSUFFICIENT_BALANCE`, `503 DEPENDENCY_UNAVAILABLE`, and sanitized `500 INTERNAL_ERROR`. Spring Security's filter-chain entry point and access-denied handler serialize the same shape for authentication and authorization failures. Unexpected exception details are logged server-side and are not returned to clients. See [Complete Phase 4 Testing Guide](../phase-4.md) for test cases.
