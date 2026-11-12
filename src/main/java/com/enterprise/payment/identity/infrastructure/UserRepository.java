package com.enterprise.payment.identity.infrastructure;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
  Optional<UserEntity> findByEmailIgnoreCase(String email);
}
