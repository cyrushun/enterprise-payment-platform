package com.enterprise.payment.security;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class OwnershipService {
  private final JdbcTemplate jdbc;

  public OwnershipService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void requireAccountOwner(UUID accountId, UUID customerId) {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from accounts where id=? and customer_id=?",
            Integer.class,
            accountId,
            customerId);
    if (count == null || count == 0)
      throw new AccessDeniedException("Account is not owned by authenticated customer");
  }

  public void requireTransferAccountOwnership(UUID transferId, UUID customerId) {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from transfers t join accounts a on a.id=t.from_account_id or a.id=t.to_account_id where t.id=? and a.customer_id=?",
            Integer.class,
            transferId,
            customerId);
    if (count == null || count == 0)
      throw new AccessDeniedException("Transfer is not visible to authenticated customer");
  }
}
