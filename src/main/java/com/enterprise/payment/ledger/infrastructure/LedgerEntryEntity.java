package com.enterprise.payment.ledger.infrastructure;

import com.enterprise.payment.ledger.domain.EntryType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntryEntity {
    @Id private UUID id;
    @Column(name = "ledger_transaction_id", nullable = false) private UUID ledgerTransactionId;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Enumerated(EnumType.STRING) @Column(name = "entry_type", nullable = false, length = 6) private EntryType entryType;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected LedgerEntryEntity() {}
    public LedgerEntryEntity(UUID journalId, UUID accountId, EntryType type, BigDecimal amount, String currency) {
        this.id = UUID.randomUUID(); this.ledgerTransactionId = journalId; this.accountId = accountId;
        this.entryType = type; this.amount = amount; this.currency = currency; this.createdAt = Instant.now();
    }
}
