# Component Diagram

```mermaid
flowchart TB
    subgraph Adapters[Inbound Adapters]
      REST[REST Controllers]
    end
    subgraph Application[Application Layer]
      CU[Customer Use Cases]
      AU[Account Use Cases]
      TU[Transfer Use Cases]
      LU[Ledger Posting Use Cases]
      RU[Reconciliation Use Cases]
    end
    subgraph Domain[Domain Layer]
      CD[Customer Model]
      AD[Account Model]
      TD[Transfer Model]
      LD[Ledger Journal / Entries]
    end
    subgraph Outbound[Outbound Adapters]
      JPA[JPA Repositories]
      PG[(PostgreSQL)]
      REDIS[(Redis)]
      OUT[Outbox Publisher - Phase 3]
      KAFKA[(Kafka - Phase 3)]
    end
    REST --> CU
    REST --> AU
    REST --> TU
    CU --> CD
    AU --> AD
    TU --> TD
    TU --> LU
    LU --> LD
    CU --> JPA
    AU --> JPA
    TU --> JPA
    LU --> JPA
    JPA --> PG
    REST -->|rate-limit counters (Phase 4.7)| REDIS
    PG --> OUT
    OUT -. later .-> KAFKA
```

The diagram is logical. It does not imply every component is implemented in Phase 0.
