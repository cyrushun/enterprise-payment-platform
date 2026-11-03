# Interview Talking Points

Use these as prompts for explaining decisions; update with implementation evidence as the project evolves.

## Why a modular monolith first?
It keeps the transfer and ledger transaction local and atomic while allowing clear module boundaries. Microservices are an option, not a default.

## Why PostgreSQL instead of Redis for balances?
Balances and ledger postings require durable ACID transactions, constraints, and row-level locks. Redis is not the financial source of truth.

## Why BigDecimal?
Binary floating-point cannot exactly represent many decimal fractions. Money needs explicit decimal precision, scale, and rounding rules.

## Why double-entry ledger?
It creates an auditable record where every posted journal balances. Corrections are compensating entries rather than destructive edits.

## Why idempotency?
Clients retry after timeouts and networks fail. A durable key plus request hash prevents duplicate financial effects and detects key reuse with a different payload.

## Why deterministic row locking?
Two opposite-direction transfers can deadlock if they lock accounts in inconsistent order. Locking account IDs in a consistent order reduces this risk.

## Why transactional outbox?
A database commit and Kafka publish are not one atomic transaction. Persisting event intent with business state avoids the dual-write gap; consumers still need deduplication.
