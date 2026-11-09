package com.enterprise.payment.audit.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {
  @Id private UUID id;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(nullable = false)
  private String action;

  @Column(name = "resource_type", nullable = false)
  private String resourceType;

  @Column(name = "resource_id")
  private UUID resourceId;

  @Column(name = "correlation_id")
  private String correlationId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private JsonNode details;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected AuditLogEntity() {}

  public UUID getId() {
    return id;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getAction() {
    return action;
  }

  public String getResourceType() {
    return resourceType;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public JsonNode getDetails() {
    return details;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public AuditLogEntity(
      UUID actor, String action, String type, UUID resource, String correlation, JsonNode details) {
    id = UUID.randomUUID();
    actorId = actor;
    this.action = action;
    resourceType = type;
    resourceId = resource;
    correlationId = correlation;
    this.details = details;
    occurredAt = Instant.now();
  }
}
