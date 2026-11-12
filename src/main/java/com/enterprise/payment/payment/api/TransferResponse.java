package com.enterprise.payment.payment.api;

import com.enterprise.payment.payment.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
    UUID id,
    String reference,
    UUID sourceAccountId,
    UUID destinationAccountId,
    BigDecimal amount,
    String currency,
    TransferStatus status,
    Instant createdAt) {}
