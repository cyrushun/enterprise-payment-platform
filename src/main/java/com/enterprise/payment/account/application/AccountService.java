package com.enterprise.payment.account.application;

import com.enterprise.payment.account.api.*;
import com.enterprise.payment.account.infrastructure.*;
import com.enterprise.payment.customer.infrastructure.CustomerRepository;
import com.enterprise.payment.customer.domain.CustomerStatus;
import com.enterprise.payment.shared.error.ResourceNotFoundException;
import java.security.SecureRandom;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accounts; private final CustomerRepository customers; private final SecureRandom random = new SecureRandom();
    public AccountService(AccountRepository accounts, CustomerRepository customers) { this.accounts = accounts; this.customers = customers; }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        var customer = customers.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Customer not found"));
        if (customer.getStatus() != CustomerStatus.ACTIVE) throw new InvalidAccountOperationException("Only active customers can open accounts");
        String number = generateAccountNumber();
        return toResponse(accounts.save(new AccountEntity(customer.getId(), number, request.accountType(), request.currency())));
    }
    @Transactional(readOnly = true)
    public AccountResponse get(UUID id) { return accounts.findById(id).map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Account not found")); }
    @Transactional(readOnly = true)
    public AccountBalanceResponse getBalance(UUID id) {
        AccountEntity account = accounts.findById(id).orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Account not found"));
        return new AccountBalanceResponse(account.getId(), account.getBalance(), account.getCurrency());
    }
    private String generateAccountNumber() {
        for (int i = 0; i < 10; i++) {
            String candidate = String.format("%016d", random.nextLong(10_000_000_000_000_000L));
            if (!accounts.existsByAccountNumber(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to allocate a unique account number");
    }
    private AccountResponse toResponse(AccountEntity a) {
        return new AccountResponse(a.getId(), a.getCustomerId(), a.getAccountNumber(), a.getAccountType(),
                a.getCurrency(), a.getBalance(), a.getStatus(), a.getOpenedAt());
    }
}
