package com.enterprise.payment.ledger.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_transactions")
public class LedgerTransactionEntity {
    @Id private UUID id;
    @Column(name = "transfer_id") private UUID transferId;
    @Column(name = "transaction_type", nullable = false, length = 32) private String transactionType;
    @Column(nullable = false, length = 20) private String status;
    @Column(length = 255) private String description;
    @Column(name = "effective_at", nullable = false) private Instant effectiveAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected LedgerTransactionEntity() {}
    public LedgerTransactionEntity(UUID transferId, String description) {
        this.id = UUID.randomUUID(); this.transferId = transferId; this.transactionType = "TRANSFER";
        this.status = "POSTED"; this.description = description; this.effectiveAt = Instant.now(); this.createdAt = this.effectiveAt;
    }
    public UUID getId() { return id; }
}
