package com.enterprise.payment.notification.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "notifications",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_notification_event_recipient",
            columnNames = {"event_id", "recipient_account_id"}))
public class NotificationEntity {
  @Id private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "recipient_account_id", nullable = false)
  private UUID recipientAccountId;

  @Column(name = "notification_type", nullable = false)
  private String notificationType;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 1000)
  private String message;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected NotificationEntity() {}

  public NotificationEntity(
      UUID eventId, UUID accountId, String type, String title, String message) {
    this.id = UUID.randomUUID();
    this.eventId = eventId;
    this.recipientAccountId = accountId;
    this.notificationType = type;
    this.title = title;
    this.message = message;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public UUID getRecipientAccountId() {
    return recipientAccountId;
  }

  public String getNotificationType() {
    return notificationType;
  }

  public String getTitle() {
    return title;
  }

  public String getMessage() {
    return message;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
