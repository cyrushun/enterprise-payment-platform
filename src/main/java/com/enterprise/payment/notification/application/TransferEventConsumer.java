package com.enterprise.payment.notification.application;

import com.enterprise.payment.messaging.event.TransferCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransferEventConsumer {
  private static final Logger log = LoggerFactory.getLogger(TransferEventConsumer.class);
  private final ObjectMapper mapper;
  private final NotificationService notifications;

  public TransferEventConsumer(ObjectMapper mapper, NotificationService notifications) {
    this.mapper = mapper;
    this.notifications = notifications;
  }

  @KafkaListener(topics = "${app.kafka.transfer-topic}", groupId = "payment-notification-service")
  public void consume(String json) throws Exception {
    TransferCompletedEvent event = mapper.readValue(json, TransferCompletedEvent.class);
    notifications.process(event);
    log.info(
        "Processed transfer event eventId={} transferId={}", event.eventId(), event.transferId());
  }
}
