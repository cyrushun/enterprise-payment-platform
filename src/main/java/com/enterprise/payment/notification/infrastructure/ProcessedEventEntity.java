package com.enterprise.payment.notification.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {
  @Id
  @Column(name = "event_id")
  private UUID eventId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "consumer_name", nullable = false)
  private String consumerName;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  protected ProcessedEventEntity() {}

  public ProcessedEventEntity(UUID id, String type, String consumer) {
    eventId = id;
    eventType = type;
    consumerName = consumer;
    processedAt = Instant.now();
  }
}
