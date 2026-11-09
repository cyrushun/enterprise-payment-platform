# Phase 2 — Transfer, idempotency and ledger verification

## Prerequisites
Complete Phase 1. Create two active accounts with the same currency and fund the source account only through the documented local test seed.

## Procedure
1. Run `mvn clean test`; import `Enterprise-Payment-Platform-Phase2.postman_collection.json`.
2. Set `sourceAccountId` and `destinationAccountId`; run successful transfer. Expect 201 and `COMPLETED`.
3. Replay same key and same payload; verify same transfer ID and no duplicate transfer/journal.
4. Reuse key with different payload; expect 409 `IDEMPOTENCY_CONFLICT`.
5. Transfer more than available balance; expect 422 `INSUFFICIENT_BALANCE`; balances and ledger must remain unchanged.
6. Query `transfers`, `ledger_transactions`, `ledger_entries`. For each completed transfer verify debit sum = credit sum = transfer amount, and account balances reflect the postings.
7. Run concurrent same-key requests in integration test; assert exactly one logical transfer and one journal transaction.
8. Verify malformed/missing `Idempotency-Key` is rejected according to the API contract; no absent key may create a transfer.

## Acceptance
Atomicity, balance protection, journal balance and idempotency hold under retry/concurrency. Phase 1 APIs remain compatible.

## Evidence
Maven/Testcontainers output, Postman run summary and before/after SQL snapshots from disposable data.
