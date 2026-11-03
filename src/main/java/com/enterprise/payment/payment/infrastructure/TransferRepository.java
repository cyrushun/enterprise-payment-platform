package com.enterprise.payment.payment.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<TransferEntity, UUID> {
    Optional<TransferEntity> findByReference(String reference);
    Page<TransferEntity> findByFromAccountIdOrToAccountId(UUID fromAccountId, UUID toAccountId, Pageable pageable);
}
