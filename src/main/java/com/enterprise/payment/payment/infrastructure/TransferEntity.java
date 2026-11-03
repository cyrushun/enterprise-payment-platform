package com.enterprise.payment.payment.infrastructure;

import com.enterprise.payment.payment.domain.TransferStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class TransferEntity {
    @Id private UUID id;
    @Column(name = "from_account_id", nullable = false) private UUID fromAccountId;
    @Column(name = "to_account_id", nullable = false) private UUID toAccountId;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private TransferStatus status;
    @Column(length = 140, unique = true) private String reference;
    @Column(name = "failure_code", length = 64) private String failureCode;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected TransferEntity() {}
    public TransferEntity(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String currency, String reference) {
        this.id = UUID.randomUUID(); this.fromAccountId = fromAccountId; this.toAccountId = toAccountId;
        this.amount = amount; this.currency = currency; this.reference = reference;
        this.status = TransferStatus.COMPLETED; this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
    public UUID getId() { return id; } public UUID getFromAccountId() { return fromAccountId; }
    public UUID getToAccountId() { return toAccountId; } public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; } public TransferStatus getStatus() { return status; }
    public String getReference() { return reference; } public Instant getCreatedAt() { return createdAt; }
}
