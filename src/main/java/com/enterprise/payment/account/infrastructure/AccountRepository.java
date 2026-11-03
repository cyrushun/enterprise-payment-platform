package com.enterprise.payment.account.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
    boolean existsByAccountNumber(String accountNumber);

    /** Locks rows in a stable order to reduce deadlocks for opposite-direction transfers. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where a.id in :ids order by a.id")
    List<AccountEntity> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);
}
