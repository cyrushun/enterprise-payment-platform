package com.enterprise.payment.outbox.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
  @Query(
      value =
          "SELECT * FROM outbox_events WHERE ((status IN ('PENDING','RETRY') AND available_at <= CURRENT_TIMESTAMP) OR (status='PROCESSING' AND locked_until < CURRENT_TIMESTAMP)) ORDER BY created_at LIMIT :batchSize FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<OutboxEventEntity> lockNextBatch(@Param("batchSize") int batchSize);
}
