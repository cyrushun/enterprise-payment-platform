package com.enterprise.payment.notification.application;

import com.enterprise.payment.messaging.event.TransferCompletedEvent;
import com.enterprise.payment.notification.infrastructure.*;
import java.math.RoundingMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationService {
  private static final String CONSUMER = "transfer-notification-consumer";
  private final ProcessedEventRepository processed;
  private final NotificationRepository notifications;
  private final io.micrometer.core.instrument.MeterRegistry metrics;

  @org.springframework.beans.factory.annotation.Autowired
  public NotificationService(
      ProcessedEventRepository p,
      NotificationRepository n,
      io.micrometer.core.instrument.MeterRegistry metrics) {
    processed = p;
    notifications = n;
    this.metrics = metrics;
  }

  public NotificationService(ProcessedEventRepository p, NotificationRepository n) {
    this(p, n, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
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
    recordProcessedMetricAfterCommit();
  }

  /**
   * Records the processed-event metric only after a successful commit when a Spring transaction
   * synchronization is active. Direct unit-test calls (or other non-transactional invocations) have
   * no synchronization callback, so the metric is recorded immediately in that case.
   */
  private void recordProcessedMetricAfterCommit() {
    Runnable recordMetric = () -> metrics.counter("payment.notifications.processed").increment();

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              recordMetric.run();
            }
          });
    } else {
      recordMetric.run();
    }
  }

  @Transactional(readOnly = true)
  public Page<NotificationEntity> list(java.util.UUID accountId, Pageable pageable) {
    return notifications.findByRecipientAccountId(accountId, pageable);
  }
}
