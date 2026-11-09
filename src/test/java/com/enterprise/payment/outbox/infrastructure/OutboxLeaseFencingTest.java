package com.enterprise.payment.outbox.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxLeaseFencingTest {
  @Test
  void leaseTokenFencesAStalePublisherAndReplayOnlyAllowsDeadEvents() throws Exception {
    var event =
        new OutboxEventEntity(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "TransferCompleted",
            "payment.transfer-events",
            "transfer-1",
            new ObjectMapper().readTree("{\"ok\":true}"));
    UUID firstLease = event.markProcessing(30, "worker-a");
    assertTrue(event.isLease(firstLease));
    event.markFailed("simulated publish failure", true);
    assertFalse(event.isLease(firstLease));
    assertThrows(
        com.enterprise.payment.outbox.domain.InvalidOutboxStateException.class, event::replay);
    event.replay();
    assertEquals(com.enterprise.payment.outbox.domain.OutboxStatus.RETRY, event.getStatus());
    assertEquals(0, event.getAttempts());
  }
}
