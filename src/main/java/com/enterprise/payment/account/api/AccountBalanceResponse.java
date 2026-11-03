package com.enterprise.payment.account.api;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountBalanceResponse(UUID accountId, BigDecimal balance, String currency) {}
