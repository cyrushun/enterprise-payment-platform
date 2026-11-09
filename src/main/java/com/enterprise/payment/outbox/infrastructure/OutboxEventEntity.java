package com.enterprise.payment.outbox.infrastructure;

import com.enterprise.payment.outbox.domain.InvalidOutboxStateException;
import com.enterprise.payment.outbox.domain.OutboxStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {
  @Id private UUID id;

  @Column(name = "aggregate_type", nullable = false, length = 100)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false, length = 150)
  private String eventType;

  @Column(nullable = false, length = 200)
  private String topic;

  @Column(name = "event_key", nullable = false, length = 200)
  private String eventKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private JsonNode payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OutboxStatus status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "available_at", nullable = false)
  private Instant availableAt;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "lease_token")
  private UUID leaseToken;

  @Column(name = "lease_owner", length = 120)
  private String leaseOwner;

  @Column(name = "last_error", length = 2000)
  private String lastError;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  protected OutboxEventEntity() {}

  public OutboxEventEntity(
      UUID id,
      UUID aggregateId,
      String eventType,
      String topic,
      String eventKey,
      JsonNode payload) {
    this.id = id;
    this.aggregateType = "TRANSFER";
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.topic = topic;
    this.eventKey = eventKey;
    this.payload = payload;
    this.status = OutboxStatus.PENDING;
    this.attempts = 0;
    this.availableAt = Instant.now();
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getLeaseToken() {
    return leaseToken;
  }

  public String getTopic() {
    return topic;
  }

  public String getEventKey() {
    return eventKey;
  }

  public JsonNode getPayload() {
    return payload;
  }

  public int getAttempts() {
    return attempts;
  }

  public OutboxStatus getStatus() {
    return status;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public UUID markProcessing(long leaseSeconds, String owner) {
    status = OutboxStatus.PROCESSING;
    lockedUntil = Instant.now().plusSeconds(leaseSeconds);
    leaseToken = UUID.randomUUID();
    leaseOwner = owner;
    attempts++;
    return leaseToken;
  }

  public boolean isLease(UUID token) {
    return token != null && token.equals(leaseToken) && status == OutboxStatus.PROCESSING;
  }

  public void replay() {
    if (status != OutboxStatus.DEAD)
      throw new InvalidOutboxStateException("Only DEAD events can be replayed");
    status = OutboxStatus.RETRY;
    attempts = 0;
    availableAt = Instant.now();
    lockedUntil = null;
    leaseToken = null;
    leaseOwner = null;
    lastError = null;
  }

  public void markPublished() {
    status = OutboxStatus.PUBLISHED;
    publishedAt = Instant.now();
    lockedUntil = null;
    leaseToken = null;
    leaseOwner = null;
    lastError = null;
  }

  public void markFailed(String error, boolean dead) {
    status = dead ? OutboxStatus.DEAD : OutboxStatus.RETRY;
    lockedUntil = null;
    leaseToken = null;
    leaseOwner = null;
    availableAt = Instant.now().plusSeconds(Math.min(300L, 1L << Math.min(attempts, 8)));
    lastError =
        error == null
            ? "Unknown publication error"
            : error.substring(0, Math.min(error.length(), 1900));
  }
}
