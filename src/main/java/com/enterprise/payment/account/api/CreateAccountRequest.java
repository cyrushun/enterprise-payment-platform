package com.enterprise.payment.account.api;

import com.enterprise.payment.account.domain.AccountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record CreateAccountRequest(
        @NotNull UUID customerId,
        @NotNull AccountType accountType,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase code") String currency) {}
