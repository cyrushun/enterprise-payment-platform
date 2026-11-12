# Deployment Architecture

## Local development

```mermaid
flowchart TB
  Client[Postman / Browser] -->|localhost:8080| API[Spring Boot API on Windows or Compose]
  subgraph DockerDesktop[Docker Desktop / Compose]
    PG[(PostgreSQL 17)]
    Redis[(Redis 7)]
    Kafka[(Kafka KRaft)]
    Prom[Prometheus]
    Graf[Grafana]
    Jaeger[Jaeger]
  end
  API --> PG
  API --> Redis
  API --> Kafka
  API --> Jaeger
  Prom --> API
  Graf --> Prom
```

For host-run API development, use `localhost` for published dependency ports and `host.docker.internal:8080` as Prometheus target. When API is containerized, use Compose service DNS names (`postgres`, `redis`, `kafka`, `jaeger`, `payment-api`) and set `PROMETHEUS_TARGET=payment-api:8080`.

## CI/CD and Linux VM deployment

```mermaid
flowchart LR
  Dev[Developer] --> Git[GitHub repository]
  Git --> CI[GitHub Actions CI]
  CI --> Scan[CodeQL / Trivy / dependency review]
  Scan --> GHCR[GHCR immutable image digest]
  GHCR --> Staging[Staging Linux VM / Docker Compose]
  GHCR -->|approved promotion| Prod[Production Linux VM / Docker Compose]
```

Staging and production are separate environments with separate VM hosts, `.env` files, JWT keys, database credentials and persistent volumes. GitHub Environment protection gates production deployment. The workflow copies deployment code/configuration but never copies environment files or secret material. TLS termination, host firewall, private management access, backup retention and monitoring are environment responsibilities.

See the single authoritative [Phase 5 guide](../phase-5.md) for deployment, CI/CD, verification and rollback procedures.
