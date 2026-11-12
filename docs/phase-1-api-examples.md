# Phase 1 — API and Postman Testing Guide

Base URL: `http://localhost:8080`.

## Prerequisites

- PostgreSQL is running.
- Flyway has applied V1.
- Application is running with `mvn spring-boot:run`.
- Use unique email addresses for each execution.

## 1. Create a customer

```http
POST /api/v1/customers
Content-Type: application/json

{
  "firstName": "Alex",
  "lastName": "Tan",
  "email": "alex.tan@example.test",
  "phoneNumber": "+60123456789"
}
```

Expected: HTTP `201 Created`. Save the returned customer UUID as `customerId`.

## 2. Retrieve the customer

```http
GET /api/v1/customers/{{customerId}}
```

Expected: HTTP `200 OK`; the response identifies the customer created in Step 1.

Unknown valid UUID: expected HTTP `404 Not Found`.

## 3. Open an account

```http
POST /api/v1/accounts
Content-Type: application/json

{
  "customerId": "{{customerId}}",
  "accountType": "SAVINGS",
  "currency": "MYR"
}
```

Expected: HTTP `201 Created`. Save `accountId` and account number. The server generates the account number; the caller does not supply an opening balance.

## 4. Retrieve account and balance

```http
GET /api/v1/accounts/{{accountId}}
```

Expected: HTTP `200 OK`; owner and account details match the creation request.

```http
GET /api/v1/accounts/{{accountId}}/balance
```

Expected: HTTP `200 OK`; initial balance is `0.0000 MYR`.

## 5. Negative test cases

| Test | Expected |
|---|---|
| Duplicate normalized email | `409 Conflict` |
| Invalid email or missing required field | `400 Bad Request` |
| Unknown customer/account UUID | `404 Not Found` |
| Open account for non-ACTIVE customer | `422 Unprocessable Entity` |
| Invalid account type/currency | `400 Bad Request` where request validation applies |

## 6. Verify PostgreSQL

```sql
SELECT id, first_name, last_name, email, phone_number, status, created_at
FROM customers
WHERE email = 'alex.tan@example.test';

SELECT id, customer_id, account_number, account_type, currency, balance, status
FROM accounts
WHERE customer_id = '<customer-uuid>';
```

Expected: one customer and the created account; account balance is zero.

## Pass condition

Customer/account APIs return the documented results, validation and uniqueness errors are stable, and the new account starts with zero balance.

---

> **Current project context:** This document records the scope and verification details for its named phase. The current overall project status is maintained in the root [README](../README.md). Phase 5 observability, performance, deployment, CI/CD and current operational status are consolidated in the [single Phase 5 source of truth](phase-5.md).
