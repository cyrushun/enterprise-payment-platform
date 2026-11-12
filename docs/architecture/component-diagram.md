# Component Diagram — Current Implemented Architecture

```mermaid
flowchart TB
  Client[Customer / Operations / Admin] --> REST[Spring Boot REST Controllers]
  REST --> SEC[Spring Security / JWT / RBAC]
  SEC --> APP[Application Use Cases]
  APP --> DOMAIN[Customer / Account / Transfer / Ledger / Identity / Audit / Reconciliation]
  APP --> PG[(PostgreSQL system of record)]
  APP --> REDIS[(Redis rate-limit counters)]
  PG --> OUT[Transactional Outbox Publisher]
  OUT --> KAFKA[(Kafka)]
  KAFKA --> NOTIFY[Notification Consumer]
  API[Payment API] --> PROM[Prometheus metrics]
  PROM --> GRAF[Grafana dashboards]
  API --> JAEGER[Jaeger via OTLP tracing]
  GH[GitHub Actions] --> GHCR[GHCR immutable image]
  GHCR --> STG[Staging Linux VM / Compose]
  GHCR --> PROD[Production Linux VM / Compose / approval]
```

The application remains a modular monolith. PostgreSQL owns financial state; Redis is non-authoritative and used for rate-limit counters. The outbox makes event intent durable in the same database transaction as the business state; Kafka delivery is at-least-once and consumers deduplicate. Phase 5 adds observability and CI/CD without extracting the business modules into microservices.

See [System Architecture](system-architecture.md) and the [single Phase 5 source of truth](../phase-5.md) for current runtime and delivery topology.
