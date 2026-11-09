package com.enterprise.payment.account.api;

import com.enterprise.payment.account.application.AccountService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
  private final AccountService service;
  private final com.enterprise.payment.security.OwnershipService ownership;

  public AccountController(
      AccountService service, com.enterprise.payment.security.OwnershipService ownership) {
    this.service = service;
    this.ownership = ownership;
  }

  @PostMapping
  public ResponseEntity<AccountResponse> create(
      @Valid @RequestBody CreateAccountRequest request, Authentication auth) {
    requireCustomerOwner(auth, request.customerId());
    AccountResponse response = service.create(request);
    return ResponseEntity.created(URI.create("/api/v1/accounts/" + response.accountId()))
        .body(response);
  }

  @GetMapping("/{accountId}")
  public AccountResponse get(@PathVariable UUID accountId, Authentication auth) {
    requireAccountOwner(auth, accountId);
    return service.get(accountId);
  }

  @GetMapping("/{accountId}/balance")
  public AccountBalanceResponse balance(@PathVariable UUID accountId, Authentication auth) {
    requireAccountOwner(auth, accountId);
    return service.getBalance(accountId);
  }

  private void requireAccountOwner(Authentication auth, UUID accountId) {
    if (auth == null || !auth.isAuthenticated()) return;
    if (hasPrivilegedRole(auth)) return;
    if (auth instanceof JwtAuthenticationToken jwt) {
      String customer = jwt.getToken().getClaimAsString("customer_id");
      if (customer == null) throw new AccessDeniedException("Customer identity is missing");
      ownership.requireAccountOwner(accountId, UUID.fromString(customer));
    }
  }

  private void requireCustomerOwner(Authentication auth, UUID customerId) {
    if (auth == null || !auth.isAuthenticated() || hasPrivilegedRole(auth)) return;
    if (auth instanceof JwtAuthenticationToken jwt) {
      String customer = jwt.getToken().getClaimAsString("customer_id");
      if (customer == null || !customerId.equals(UUID.fromString(customer)))
        throw new AccessDeniedException("Customer may only create accounts for their own profile");
    }
  }

  private boolean hasPrivilegedRole(Authentication auth) {
    return auth.getAuthorities().stream()
        .anyMatch(
            a ->
                a.getAuthority().equals("ROLE_OPERATIONS")
                    || a.getAuthority().equals("ROLE_ADMIN"));
  }
}
