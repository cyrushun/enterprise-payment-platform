package com.enterprise.payment.account.infrastructure;

import com.enterprise.payment.account.domain.AccountStatus;
import com.enterprise.payment.account.domain.AccountType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class AccountEntity {
    @Id private UUID id;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "account_number", nullable = false, unique = true, length = 34) private String accountNumber;
    @Enumerated(EnumType.STRING) @Column(name = "account_type", nullable = false, length = 20) private AccountType accountType;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal balance;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AccountStatus status;
    @Version @Column(nullable = false) private long version;
    @Column(name = "opened_at", nullable = false, updatable = false) private Instant openedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected AccountEntity() {}
    public AccountEntity(UUID customerId, String accountNumber, AccountType accountType, String currency) {
        this.id = UUID.randomUUID(); this.customerId = customerId; this.accountNumber = accountNumber;
        this.accountType = accountType; this.currency = currency; this.balance = BigDecimal.ZERO.setScale(4);
        this.status = AccountStatus.ACTIVE; this.version = 0; this.openedAt = Instant.now(); this.updatedAt = this.openedAt;
    }
    public UUID getId() { return id; } public UUID getCustomerId() { return customerId; }
    public String getAccountNumber() { return accountNumber; } public AccountType getAccountType() { return accountType; }
    public String getCurrency() { return currency; } public BigDecimal getBalance() { return balance; }
    public AccountStatus getStatus() { return status; } public long getVersion() { return version; }
    public Instant getOpenedAt() { return openedAt; }
}
