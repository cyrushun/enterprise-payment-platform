package com.enterprise.payment.outbox.api;

import com.enterprise.payment.outbox.infrastructure.OutboxEventEntity;
import java.time.Instant;
import java.util.UUID;

public record OutboxEventResponse(
    UUID id,
    String topic,
    String eventKey,
    String status,
    int attempts,
    String payload,
    String lastError,
    Instant createdAt,
    Instant publishedAt) {
  public static OutboxEventResponse from(OutboxEventEntity e) {
    return new OutboxEventResponse(
        e.getId(),
        e.getTopic(),
        e.getEventKey(),
        e.getStatus().name(),
        e.getAttempts(),
        e.getPayload().toString(),
        e.getLastError(),
        e.getCreatedAt(),
        e.getPublishedAt());
  }
}
