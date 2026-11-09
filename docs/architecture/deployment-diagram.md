# Local Deployment Diagram

```mermaid
flowchart TB
    IDE[IntelliJ IDEA / Local JVM]
    Browser[HTTP Client / Swagger later]
    subgraph DockerDesktop[Docker Desktop]
      PG[(PostgreSQL 17)]
      Redis[(Redis 7)]
    end
    Browser -->|localhost:8080| IDE
    IDE -->|localhost:5432| PG
    IDE -->|localhost:6379| Redis
```

The Spring Boot application runs on the Windows host during development. PostgreSQL and Redis run in Docker containers. In a containerized application deployment, use Compose service DNS names (for example `postgres`) rather than `localhost`.
