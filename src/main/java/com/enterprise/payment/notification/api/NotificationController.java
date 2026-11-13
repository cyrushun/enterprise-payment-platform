package com.enterprise.payment.notification.api;

import com.enterprise.payment.notification.application.NotificationService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final NotificationService service;
  private final com.enterprise.payment.security.OwnershipService ownership;

  public NotificationController(
      NotificationService service, com.enterprise.payment.security.OwnershipService ownership) {
    this.service = service;
    this.ownership = ownership;
  }

  @GetMapping
  public Page<NotificationResponse> list(
      @RequestParam UUID accountId, Pageable pageable, Authentication auth) {
    requireAccountOwner(auth, accountId);
    return service.list(accountId, pageable).map(NotificationResponse::from);
  }

  private void requireAccountOwner(Authentication auth, UUID accountId) {
    if (auth == null || !auth.isAuthenticated())
      throw new AccessDeniedException("Authentication is required");
    boolean privileged =
        auth.getAuthorities().stream()
            .anyMatch(
                a ->
                    a.getAuthority().equals("ROLE_OPERATIONS")
                        || a.getAuthority().equals("ROLE_ADMIN"));
    if (privileged) return;
    if (auth instanceof JwtAuthenticationToken jwt
        && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"))) {
      String customerId = jwt.getToken().getClaimAsString("customer_id");
      if (customerId == null) throw new AccessDeniedException("Customer identity is missing");
      ownership.requireAccountOwner(accountId, UUID.fromString(customerId));
      return;
    }
    throw new AccessDeniedException("Customer account ownership could not be verified");
  }
}
