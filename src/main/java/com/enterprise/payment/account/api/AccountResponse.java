package com.enterprise.payment.account.api;

import com.enterprise.payment.account.domain.AccountStatus;
import com.enterprise.payment.account.domain.AccountType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID accountId, UUID customerId, String accountNumber, AccountType accountType,
                              String currency, BigDecimal balance, AccountStatus status, Instant openedAt) {}
