package com.enterprise.payment.account.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
    boolean existsByAccountNumber(String accountNumber);
}
