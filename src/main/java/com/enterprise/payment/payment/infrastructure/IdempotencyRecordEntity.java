package com.enterprise.payment.payment.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "idempotency_records",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_idempotency_scope_key",
            columnNames = {"operation_scope", "idempotency_key"}))
public class IdempotencyRecordEntity {
  @Id private UUID id;

  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String idempotencyKey;

  @Column(name = "operation_scope", nullable = false, length = 100)
  private String operationScope;

  @Column(name = "request_hash", nullable = false, length = 128)
  private String requestHash;

  @Column(name = "resource_id")
  private UUID resourceId;

  @Column(name = "response_status")
  private Integer responseStatus;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected IdempotencyRecordEntity() {}

  public IdempotencyRecordEntity(String scope, String key, String requestHash, UUID resourceId) {
    this.id = UUID.randomUUID();
    this.operationScope = scope;
    this.idempotencyKey = key;
    this.requestHash = requestHash;
    this.resourceId = resourceId;
    this.responseStatus = 201;
    this.status = "COMPLETED";
    this.createdAt = Instant.now();
  }

  public String getRequestHash() {
    return requestHash;
  }

  public UUID getResourceId() {
    return resourceId;
  }
}
