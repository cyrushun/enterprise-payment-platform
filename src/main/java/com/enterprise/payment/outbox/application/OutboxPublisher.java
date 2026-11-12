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
  private final io.micrometer.tracing.Tracer tracer;

  @Value("${app.outbox.batch-size:25}")
  private int batchSize;

  @Value("${app.outbox.max-attempts:8}")
  private int maxAttempts;

  @Value("${app.outbox.send-timeout-seconds:10}")
  private long sendTimeoutSeconds;

  public OutboxPublisher(
      OutboxService outbox,
      KafkaTemplate<String, String> kafka,
      MeterRegistry metrics,
      io.micrometer.tracing.Tracer tracer) {
    this.outbox = outbox;
    this.kafka = kafka;
    this.metrics = metrics;
    this.tracer = tracer;
  }

  private io.micrometer.tracing.Span spanFromTraceparent(String traceparent) {
    if (traceparent == null) return null;
    String[] parts = traceparent.split("-", -1);
    if (parts.length != 4
        || !parts[0].equals("00")
        || !parts[1].matches("[0-9a-f]{32}")
        || !parts[2].matches("[0-9a-f]{16}")) return null;
    io.micrometer.tracing.TraceContext parent =
        tracer
            .traceContextBuilder()
            .traceId(parts[1])
            .spanId(parts[2])
            .sampled(parts[3].endsWith("1"))
            .build();
    return tracer.spanBuilder().setParent(parent).name("outbox.publish").start();
  }

  public void publishBatch() {
    List<OutboxEventEntity> batch = outbox.claim(batchSize);
    for (OutboxEventEntity event : batch) {
      long publishStarted = System.nanoTime();
      String outcome = "success";
      try {
        org.apache.kafka.clients.producer.ProducerRecord<String, String> record =
            new org.apache.kafka.clients.producer.ProducerRecord<>(
                event.getTopic(), event.getEventKey(), event.getPayload().toString());
        if (event.getCorrelationId() != null)
          record
              .headers()
              .add(
                  "X-Correlation-ID",
                  event.getCorrelationId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (event.getTracestate() != null)
          record
              .headers()
              .add(
                  "tracestate",
                  event.getTracestate().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        io.micrometer.tracing.Span parent = spanFromTraceparent(event.getTraceparent());
        if (parent != null) {
          try (io.micrometer.tracing.Tracer.SpanInScope ignored = tracer.withSpan(parent)) {
            kafka.send(record).get(sendTimeoutSeconds, TimeUnit.SECONDS);
          } finally {
            parent.end();
          }
        } else {
          kafka.send(record).get(sendTimeoutSeconds, TimeUnit.SECONDS);
        }
        if (outbox.published(event.getId(), event.getLeaseToken())) {
          metrics.counter("payment.outbox.published").increment();
        } else {
          outcome = "lease_lost";
        }
      } catch (Exception ex) {
        outcome = "failure";
        log.error("Outbox publication failed eventId={}", event.getId(), ex);
        if (outbox.failed(event.getId(), event.getLeaseToken(), ex.getMessage(), maxAttempts))
          metrics.counter("payment.outbox.failed").increment();
      } finally {
        metrics
            .timer("payment.outbox.publish.duration", "outcome", outcome)
            .record(System.nanoTime() - publishStarted, TimeUnit.NANOSECONDS);
      }
    }
  }
}
