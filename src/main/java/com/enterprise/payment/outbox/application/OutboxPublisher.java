package com.enterprise.payment.outbox.application;

import com.enterprise.payment.outbox.infrastructure.OutboxEventEntity;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private final OutboxService outbox;
  private final KafkaTemplate<String, String> kafka;

  @Value("${app.outbox.batch-size:25}")
  private int batchSize;

  @Value("${app.outbox.max-attempts:8}")
  private int maxAttempts;

  public OutboxPublisher(OutboxService outbox, KafkaTemplate<String, String> kafka) {
    this.outbox = outbox;
    this.kafka = kafka;
  }

  public void publishBatch() {
    List<OutboxEventEntity> batch = outbox.claim(batchSize);
    for (OutboxEventEntity event : batch) {
      try {
        kafka
            .send(event.getTopic(), event.getEventKey(), event.getPayload().toString())
            .get(10, TimeUnit.SECONDS);
        outbox.published(event.getId());
      } catch (Exception ex) {
        log.error("Outbox publication failed eventId={}", event.getId(), ex);
        outbox.failed(event.getId(), ex.getMessage(), maxAttempts);
      }
    }
  }
}
