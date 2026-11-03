package com.enterprise.payment.notification.infrastructure;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
  Page<NotificationEntity> findByRecipientAccountId(UUID accountId, Pageable pageable);
}
