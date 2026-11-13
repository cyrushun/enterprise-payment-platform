# Non-Functional Requirements

These requirements define the project quality targets. Phases 0–5 implement the corresponding capabilities incrementally; completion status and environment-specific verification evidence are recorded in the phase guides and README.

| Area | Requirement / validation |
|---|---|
| Correctness | Atomic transfer and balanced ledger under success/failure |
| Concurrency | No overdraft under simultaneous debits |
| Idempotency | Duplicate retries create no duplicate financial effect |
| Availability | Graceful startup/shutdown and dependency health checks |
| Resilience | Bounded retries/timeouts, Kafka DLT and outbox recovery (Phase 4); deployment health checks and image rollback baseline (Phases 5.3–5.4) |
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


## Current delivery and operations baseline

Phase 5.1 adds metrics, tracing and structured logs; Phase 5.2 provides repeatable load-test and diagnostic tooling; Phase 5.3 provides Docker Compose deployment readiness; Phase 5.4 provides GitHub Actions/GHCR/Linux VM delivery automation. The project owner has announced Phase 5 verification complete. This milestone does not imply regulatory certification or a formal production-readiness audit. See the [single Phase 5 guide](../phase-5.md).
