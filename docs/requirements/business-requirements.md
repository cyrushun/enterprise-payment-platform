# Business Requirements

## 1. Product vision

Build a simulated retail banking payment platform that supports customer accounts, internal fund transfers, ledger-based financial records, payment lifecycle management, auditability, and operational reconciliation.

The project is an engineering portfolio and learning system, not a production banking product.

## 2. Actors

- Customer: owns accounts and initiates transfers.
- Operations user: reviews operational activity and reconciliation results.
- System operator: monitors application health and background processing.
- Internal service: processes events and notifications.

Authorization must be enforced server-side. A caller must never gain access to an account by changing an account ID in a request.

## 3. MVP capabilities

### Customer
- Create and retrieve a customer profile.
- Suspend or close a customer subject to account-state rules.

### Account
- Open a savings or current account.
- Assign a unique account number.
- Support a currency per account.
- Retrieve account status and balance.
- Freeze/unfreeze an account through an authorized operation.

### Internal transfer
- Transfer funds between two eligible accounts in the same currency.
- Reject non-positive amounts, self-transfers, inactive/frozen accounts, and insufficient funds.
- Return a stable transfer identifier and status.
- Safely retry a request using an idempotency key.
- Record balanced double-entry ledger postings atomically.

### Ledger
- Record debit and credit entries for every posted financial event.
- Prevent modification/deletion of posted entries through application permissions and service design.
- Support transaction history and reconciliation.

### Operations
- Maintain audit events for sensitive operations.
- Publish domain events through a transactional outbox.
- Detect balance-to-ledger discrepancies.

## 4. Out of scope for the portfolio MVP

- Real bank rails, SWIFT, DuitNow, FPX, card acquiring, or actual money movement.
- KYC/AML certification or regulatory compliance claims.
- Interest calculation, fees, FX conversion, overdraft facilities.
- Multi-bank settlement.
- Real customer PII or real credentials.

## 5. Key business rules

1. Account balance must never become negative for the supported MVP account types.
2. Transfer amount must be greater than zero and use the account currency.
3. Source and destination accounts must be distinct and active.
4. Source and destination accounts must have the same currency.
5. A successful transfer creates exactly one logical journal with at least one debit and one credit.
6. Total debit equals total credit for a posted journal, per currency.
7. A failed transfer must not leave a partial debit, credit, or balance update.
8. Repeating the same idempotency key and same request returns the original outcome.
9. Reusing a key with a different request payload is rejected.
10. Posted ledger entries are immutable; corrections use compensating entries.
11. Every state transition is timestamped and traceable.

## 6. Success criteria

- Deterministic integration tests prove atomicity and rollback.
- Concurrency tests prove no overdraft under simultaneous transfers.
- Idempotency tests prove duplicate requests do not duplicate financial effects.
- Ledger tests prove posted journals balance.
- Reconciliation can detect deliberately injected test discrepancies.
- Performance results report environment, workload, p50, p95, p99, throughput, and error rate.
