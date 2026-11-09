package com.enterprise.payment.outbox.application;

import com.enterprise.payment.outbox.infrastructure.OutboxEventEntity;
import io.micrometer.core.instrument.MeterRegistry;
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
  private final MeterRegistry metrics;

  @Value("${app.outbox.batch-size:25}")
  private int batchSize;

  @Value("${app.outbox.max-attempts:8}")
  private int maxAttempts;

  @Value("${app.outbox.send-timeout-seconds:10}")
  private long sendTimeoutSeconds;

  public OutboxPublisher(
      OutboxService outbox, KafkaTemplate<String, String> kafka, MeterRegistry metrics) {
    this.outbox = outbox;
    this.kafka = kafka;
    this.metrics = metrics;
  }

  public void publishBatch() {
    List<OutboxEventEntity> batch = outbox.claim(batchSize);
    for (OutboxEventEntity event : batch) {
      try {
        kafka
            .send(event.getTopic(), event.getEventKey(), event.getPayload().toString())
            .get(sendTimeoutSeconds, TimeUnit.SECONDS);
        if (outbox.published(event.getId(), event.getLeaseToken()))
          metrics.counter("payment.outbox.published").increment();
      } catch (Exception ex) {
        log.error("Outbox publication failed eventId={}", event.getId(), ex);
        if (outbox.failed(event.getId(), event.getLeaseToken(), ex.getMessage(), maxAttempts))
          metrics.counter("payment.outbox.failed").increment();
      }
    }
  }
}
