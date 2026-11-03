package com.enterprise.payment.customer.api;

import com.enterprise.payment.customer.domain.CustomerStatus;
import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(UUID customerId, String firstName, String lastName, String email,
                               String phoneNumber, CustomerStatus status, Instant createdAt) {}
