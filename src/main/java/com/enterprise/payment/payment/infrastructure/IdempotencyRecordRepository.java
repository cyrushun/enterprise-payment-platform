package com.enterprise.payment.payment.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, UUID> {
  Optional<IdempotencyRecordEntity> findByOperationScopeAndIdempotencyKey(
      String operationScope, String idempotencyKey);
}
