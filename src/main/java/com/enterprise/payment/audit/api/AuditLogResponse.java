package com.enterprise.payment.audit.api;

import com.enterprise.payment.audit.infrastructure.AuditLogEntity;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    UUID actorId,
    String action,
    String resourceType,
    UUID resourceId,
    String correlationId,
    String details,
    Instant occurredAt) {
  public static AuditLogResponse from(AuditLogEntity e) {
    return new AuditLogResponse(
        e.getId(),
        e.getActorId(),
        e.getAction(),
        e.getResourceType(),
        e.getResourceId(),
        e.getCorrelationId(),
        e.getDetails() == null ? null : e.getDetails().toString(),
        e.getOccurredAt());
  }
}
