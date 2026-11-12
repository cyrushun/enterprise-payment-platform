package com.enterprise.payment.notification.application;

import com.enterprise.payment.messaging.event.TransferCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class TransferEventConsumer {
  private static final Logger log = LoggerFactory.getLogger(TransferEventConsumer.class);
  private final ObjectMapper mapper;
  private final NotificationService notifications;
  private final MeterRegistry metrics;

  public TransferEventConsumer(
      ObjectMapper mapper, NotificationService notifications, MeterRegistry metrics) {
    this.mapper = mapper;
    this.notifications = notifications;
    this.metrics = metrics;
  }

  @KafkaListener(
      topics = "${app.kafka.transfer-topic}",
      groupId = "payment-notification-service",
      concurrency = "${app.kafka.listener-concurrency:1}")
  public void consume(
      String json, @Header(name = "X-Correlation-ID", required = false) String correlationId)
      throws Exception {
    if (correlationId != null && correlationId.matches("[A-Za-z0-9._-]{1,128}")) {
      MDC.put("correlationId", correlationId);
    }
    long started = System.nanoTime();
    String outcome = "success";
    try {
      TransferCompletedEvent event = mapper.readValue(json, TransferCompletedEvent.class);
      notifications.process(event);
      metrics.counter("payment.kafka.consumer.messages", "outcome", "success").increment();
      log.info(
          "Processed transfer event eventId={} transferId={}", event.eventId(), event.transferId());
    } catch (Exception exception) {
      outcome = "failure";
      metrics.counter("payment.kafka.consumer.messages", "outcome", "failure").increment();
      throw exception;
    } finally {
      metrics
          .timer("payment.kafka.consumer.processing", "outcome", outcome)
          .record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
      MDC.remove("correlationId");
    }
  }
}
