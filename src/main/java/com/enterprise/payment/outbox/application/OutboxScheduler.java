package com.enterprise.payment.outbox.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {
  private final OutboxPublisher publisher;

  public OutboxScheduler(OutboxPublisher publisher) {
    this.publisher = publisher;
  }

  @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
  public void publish() {
    publisher.publishBatch();
  }
}
