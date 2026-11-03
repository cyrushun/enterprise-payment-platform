package com.enterprise.payment.payment.domain;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {
    private final BigDecimal availableBalance;
    private final BigDecimal requestedAmount;
    public InsufficientBalanceException(BigDecimal availableBalance, BigDecimal requestedAmount) {
        super("Insufficient account balance");
        this.availableBalance = availableBalance; this.requestedAmount = requestedAmount;
    }
    public BigDecimal getAvailableBalance() { return availableBalance; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
}
