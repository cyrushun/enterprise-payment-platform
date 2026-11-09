package com.enterprise.payment.identity.infrastructure;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class RoleEntity {
  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String name;

  protected RoleEntity() {}

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
