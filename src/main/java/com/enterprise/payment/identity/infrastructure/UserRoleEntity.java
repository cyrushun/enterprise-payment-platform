package com.enterprise.payment.identity.infrastructure;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "user_roles")
@IdClass(UserRoleId.class)
public class UserRoleEntity {
  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Id
  @Column(name = "role_id")
  private UUID roleId;

  protected UserRoleEntity() {}

  public UserRoleEntity(UUID userId, UUID roleId) {
    this.userId = userId;
    this.roleId = roleId;
  }
}
