# API Specification — Planned Contract

The Phase 0 skeleton exposes `GET /api/v1` and Actuator health only. Business endpoints are implemented in subsequent phases.

## Planned endpoint groups

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
| POST | `/api/v1/reconciliation-runs` | 4 |

## Transfer request draft

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

## Transfer response draft

```json
{
  "transferId": "UUID",
  "status": "COMPLETED",
  "amount": "500.00",
  "currency": "MYR",
  "createdAt": "2026-11-01T10:15:30Z"
}
```

The final OpenAPI contract will be generated and checked in Phase 1/2 alongside DTOs and error handling.
