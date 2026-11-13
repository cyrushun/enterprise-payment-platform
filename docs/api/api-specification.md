# API Specification — Implemented API Baseline

This document summarizes implemented endpoints. The phase guides and Postman collections are the executable verification references.

## Endpoint groups

| Method | Path | Access / notes |
|---|---|---|
| POST | `/api/v1/auth/register` | Public; CUSTOMER role only |
| POST | `/api/v1/auth/login` | Public |
| POST | `/api/v1/auth/refresh` | Public refresh-token rotation |
| POST | `/api/v1/auth/logout` | Refresh-token revocation |
| GET | `/api/v1/auth/me` | Authenticated; includes userId, customerId, email and roles |
| POST | `/api/v1/customers` | Existing customer creation contract |
| GET | `/api/v1/customers/{customerId}` | Authenticated |
| POST | `/api/v1/accounts` | Authenticated; customer ownership enforced |
| GET | `/api/v1/accounts` | Phase 6.2; CUSTOMER scoped to JWT customer_id; OPERATIONS/ADMIN require customerId |
| GET | `/api/v1/accounts/{accountId}` | Account owner or privileged role |
| GET | `/api/v1/accounts/{accountId}/balance` | Account owner or privileged role |
| POST | `/api/v1/transfers` | Authenticated; requires Idempotency-Key and source account ownership |
| GET | `/api/v1/transfers/{id}` | Transfer owner or privileged role |
| GET | `/api/v1/transfers/reference/{reference}` | Transfer owner or privileged role |
| GET | `/api/v1/accounts/{accountId}/transfers` | Account owner or privileged role; pageable |
| GET | `/api/v1/notifications?accountId={id}` | CUSTOMER account ownership enforced; OPERATIONS/ADMIN privileged |
| GET | `/api/v1/audit-logs` | OPERATIONS/ADMIN |
| GET | `/api/v1/reconciliation/runs` | OPERATIONS/ADMIN |
| POST | `/api/v1/reconciliation/run` | OPERATIONS/ADMIN |
| GET | `/api/v1/outbox/events` | OPERATIONS/ADMIN |
| POST | `/api/v1/outbox/events/{id}/replay` | OPERATIONS/ADMIN; eligible DEAD events only |

## Transfer request

`POST /api/v1/transfers` requires `Idempotency-Key` and this request shape:

```json
{
  "sourceAccountId": "UUID",
  "destinationAccountId": "UUID",
  "amount": 500.00,
  "currency": "MYR",
  "description": "Invoice payment"
}
```

The response uses `id`, `reference`, `sourceAccountId`, `destinationAccountId`, `amount`, `currency`, `status` and `createdAt`. The frontend does not perform ledger calculations or determine final transfer status.

## Account list added for Phase 6.2

`GET /api/v1/accounts` returns an array of `AccountResponse`. A CUSTOMER's list is always scoped using the signed JWT `customer_id` claim; a supplied `customerId` query parameter cannot widen the result. OPERATIONS/ADMIN callers must supply `customerId` to identify the customer being inspected. No account-ID enumeration is used by the frontend.

## Authentication and frontend BFF

The backend auth endpoints issue access and rotating refresh tokens in JSON. The Next.js frontend calls them server-side through `/api/session/*`; tokens are kept inside an encrypted HttpOnly cookie and are not exposed to browser JavaScript. The browser calls authenticated endpoints through `/api/backend/*`. The BFF is not a substitute for backend RBAC or ownership checks. See [Phase 6](../phase-6.md) for session cryptography, route protection and verification.

## Standard errors

Errors use the shared `ApiError` shape: `code`, `message`, `timestamp` and `details`. Authentication failures return 401 and authorization failures return 403. Clients must not expose stack traces or raw internal exception details.
