package com.enterprise.payment.identity.infrastructure;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleId> {
  @Query(
      "select r.name from RoleEntity r, UserRoleEntity ur where ur.roleId=r.id and ur.userId=:userId")
  List<String> findRoleNames(@Param("userId") UUID userId);
}
