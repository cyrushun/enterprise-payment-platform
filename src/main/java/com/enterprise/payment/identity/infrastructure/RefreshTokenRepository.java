package com.enterprise.payment.identity.infrastructure;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
  Optional<RefreshTokenEntity> findByTokenHash(String hash);

  List<RefreshTokenEntity> findByFamilyId(UUID familyId);
}
