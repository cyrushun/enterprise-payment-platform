package com.enterprise.payment.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.enterprise.payment.messaging.event.TransferCompletedEvent;
import com.enterprise.payment.notification.infrastructure.NotificationEntity;
import com.enterprise.payment.notification.infrastructure.NotificationRepository;
import com.enterprise.payment.notification.infrastructure.ProcessedEventEntity;
import com.enterprise.payment.notification.infrastructure.ProcessedEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {
  @Test
  void duplicateEventDoesNotCreateDuplicateNotifications() {
    ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    NotificationRepository notifications = mock(NotificationRepository.class);
    UUID eventId = UUID.randomUUID();
    when(processed.existsById(eventId)).thenReturn(false, true);
    io.micrometer.core.instrument.simple.SimpleMeterRegistry metrics =
        new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    NotificationService service = new NotificationService(processed, notifications, metrics);
    TransferCompletedEvent event =
        new TransferCompletedEvent(
            eventId,
            "TransferCompleted",
            Instant.now(),
            UUID.randomUUID(),
            "TRF-TEST-001",
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("12.5000"),
            "MYR");

    service.process(event);
    service.process(event);

    verify(processed, times(1)).save(any(ProcessedEventEntity.class));
    verify(notifications, times(2)).save(any(NotificationEntity.class));
    assertEquals(1.0, metrics.counter("payment.notifications.processed").count());
  }
}
