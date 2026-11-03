package com.enterprise.payment.notification.api;

import com.enterprise.payment.notification.infrastructure.NotificationEntity;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    UUID eventId,
    UUID recipientAccountId,
    String notificationType,
    String title,
    String message,
    Instant createdAt) {
  public static NotificationResponse from(NotificationEntity n) {
    return new NotificationResponse(
        n.getId(),
        n.getEventId(),
        n.getRecipientAccountId(),
        n.getNotificationType(),
        n.getTitle(),
        n.getMessage(),
        n.getCreatedAt());
  }
}
