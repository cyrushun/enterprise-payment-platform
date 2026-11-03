# Non-Functional Requirements

These are design targets to validate, not claims that the current skeleton already meets them.

| Area | Requirement / validation |
|---|---|
| Correctness | Atomic transfer and balanced ledger under success/failure |
| Concurrency | No overdraft under simultaneous debits |
| Idempotency | Duplicate retries create no duplicate financial effect |
| Availability | Graceful startup/shutdown and dependency health checks |
| Resilience | Bounded retries, timeouts, dead-letter/recovery strategy in later phases |
| Security | Authentication, authorization, input validation, secret externalization |
| Privacy | No sensitive data in logs; synthetic data only |
| Performance | Measure p50/p95/p99 and throughput under documented load |
| Scalability | Stateless API instances; database and worker scaling assessed separately |
| Observability | Structured logs, metrics, correlation IDs, distributed traces |
| Maintainability | Feature-based modules, ADRs, automated tests, Flyway migrations |
| Portability | Docker Compose local environment; no host-specific paths |
| Data integrity | Foreign keys, unique constraints, checks, transaction boundaries |

## Initial performance test methodology

Do not publish arbitrary throughput claims. Define workload, data volume, machine specs, JVM options, database settings, and test duration. Report p50, p95, p99, throughput, and error rate. Separate warm-up from measurement.
