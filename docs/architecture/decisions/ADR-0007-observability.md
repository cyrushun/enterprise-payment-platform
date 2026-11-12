# ADR-0007: Application Observability

- Status: Accepted
- Date: 2026-11-11

## Context

The payment API needs metrics, distributed tracing, structured logs and correlation across the asynchronous transactional-outbox boundary without changing financial transaction semantics.

## Decision

Use Micrometer with Prometheus, Grafana for visualization, Micrometer Tracing with OpenTelemetry/OTLP to Jaeger, and Spring Boot structured Logstash JSON output. Protect `/actuator/prometheus` with a dedicated HTTP Basic `ROLE_METRICS` chain. Add correlation and W3C trace context columns to outbox records in additive Flyway V7; preserve the event JSON contract and V1–V6.

## Consequences

- Local monitoring is Docker Compose based and can be stopped independently of payment services.
- Jaeger all-in-one storage is ephemeral for development only.
- Metrics labels must remain bounded and non-sensitive.
- Monitoring credentials are externalized; local Prometheus reads a password file ignored by Git.
- When the API is containerized, scrape and OTLP endpoints use Compose service DNS. Phase 5.4 extends delivery to separate Linux VMs using GHCR image digests; see the single Phase 5 guide.
