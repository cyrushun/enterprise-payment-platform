# Phase 1 — Customer and account verification

## Procedure
1. Start dependencies and API; run `mvn clean test`.
2. Import `Enterprise-Payment-Platform-Phase1.postman_collection.json` and `Enterprise-Payment-Platform-Local.postman_environment.json`.
3. Run requests in collection order: create customer, retrieve customer, create account, retrieve account, read balance. Confirm IDs auto-populate collection variables.
4. Create a second synthetic customer and account to test isolation.
5. Repeat customer creation with the same email: expect 409. Submit malformed email, missing required fields and invalid values: expect 400 with `ApiError` fields.
6. Verify DB rows using read-only queries against `customers` and `accounts`; confirm FK ownership and expected currency/status.
7. After Phase 4 is enabled, verify a customer cannot access another customer's account (403).

## Acceptance
- Valid customer/account lifecycle succeeds; duplicate and invalid input fail with stable codes; no cross-customer data access.
- Re-run the collection after later phases to ensure API compatibility.

## Evidence
Postman run summary, sanitized response bodies, relevant SQL query output and Maven test log.
