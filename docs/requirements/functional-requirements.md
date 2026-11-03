# Functional Requirements

## Customer and account

| ID | Requirement | Acceptance |
|---|---|---|
| FR-001 | Create customer | Valid customer receives UUID and ACTIVE status |
| FR-002 | Retrieve customer | Existing customer is returned; unknown ID returns 404 |
| FR-003 | Open account | Unique account number, currency, type and initial zero balance |
| FR-004 | Retrieve account | Account details omit secrets and internal-only fields |
| FR-005 | Retrieve balance | Balance and currency are returned consistently |
| FR-006 | Freeze account | Only authorized actor can freeze an eligible account |

## Transfer and ledger

| ID | Requirement | Acceptance |
|---|---|---|
| FR-010 | Initiate transfer | Valid transfer is processed atomically |
| FR-011 | Validate funds | Insufficient funds causes no financial mutation |
| FR-012 | Validate currency | Cross-currency transfer is rejected in MVP |
| FR-013 | Idempotent request | Same key and same request do not repeat financial effect |
| FR-014 | Key payload conflict | Same key with different request hash is rejected |
| FR-015 | Ledger posting | Posted journal balances per currency |
| FR-016 | Transaction history | Entries are paginated and ordered deterministically |
| FR-017 | Ledger immutability | Posted entries cannot be edited/deleted by application flows |

## Operations and integration

| ID | Requirement | Acceptance |
|---|---|---|
| FR-020 | Audit sensitive actions | Actor, action, resource, correlation and time recorded |
| FR-021 | Outbox event | Event row commits or rolls back with business transaction |
| FR-022 | Retry publication | Failed publication is retried without losing the event |
| FR-023 | Reconciliation | Account balance can be compared with ledger-derived balance |
| FR-024 | Health endpoint | Application and dependency health are observable |

## API conventions

- Version prefix: `/api/v1`.
- JSON request/response bodies.
- UUID resource identifiers.
- ISO-8601 timestamps in UTC.
- Stable error response schema to be defined in Phase 1.
- Pagination for potentially large collections.
- Idempotency key required for money-moving write operations.
