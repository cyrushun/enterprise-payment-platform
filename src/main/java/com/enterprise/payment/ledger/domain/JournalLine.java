package com.enterprise.payment.ledger.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record JournalLine(UUID accountId, EntryType type, BigDecimal amount, String currency) {}
