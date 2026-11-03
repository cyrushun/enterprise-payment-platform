package com.enterprise.payment.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferCompletedEvent(
    UUID eventId,
    String eventType,
    Instant occurredAt,
    UUID transferId,
    String reference,
    UUID sourceAccountId,
    UUID destinationAccountId,
    BigDecimal amount,
    String currency) {}
