package com.enterprise.payment.notification.application;

import com.enterprise.payment.messaging.event.TransferCompletedEvent;
import com.enterprise.payment.notification.infrastructure.*;
import java.math.RoundingMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
  private static final String CONSUMER = "transfer-notification-consumer";
  private final ProcessedEventRepository processed;
  private final NotificationRepository notifications;

  public NotificationService(ProcessedEventRepository p, NotificationRepository n) {
    processed = p;
    notifications = n;
  }

  @Transactional
  public void process(TransferCompletedEvent event) {
    if (processed.existsById(event.eventId())) return;
    processed.save(new ProcessedEventEntity(event.eventId(), event.eventType(), CONSUMER));
    String amount =
        event.amount().setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + event.currency();
    notifications.save(
        new NotificationEntity(
            event.eventId(),
            event.sourceAccountId(),
            event.eventType(),
            "Transfer sent",
            "You sent " + amount + ". Reference: " + event.reference()));
    notifications.save(
        new NotificationEntity(
            event.eventId(),
            event.destinationAccountId(),
            event.eventType(),
            "Transfer received",
            "You received " + amount + ". Reference: " + event.reference()));
  }

  @Transactional(readOnly = true)
  public Page<NotificationEntity> list(java.util.UUID accountId, Pageable pageable) {
    return notifications.findByRecipientAccountId(accountId, pageable);
  }
}
