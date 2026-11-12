package com.enterprise.payment.identity.infrastructure;

import java.io.Serializable;
import java.util.UUID;

public class UserRoleId implements Serializable {
  public UUID userId;
  public UUID roleId;

  public UserRoleId() {}

  public UserRoleId(UUID u, UUID r) {
    userId = u;
    roleId = r;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof UserRoleId x
        && java.util.Objects.equals(userId, x.userId)
        && java.util.Objects.equals(roleId, x.roleId);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(userId, roleId);
  }
}
