package com.enterprise.payment.account.api;

import com.enterprise.payment.account.application.AccountService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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

  @GetMapping
  public List<AccountResponse> list(
      @RequestParam(required = false) UUID customerId, Authentication auth) {
    if (auth == null || !auth.isAuthenticated())
      throw new AccessDeniedException("Authentication is required");
    if (!(auth instanceof JwtAuthenticationToken jwt)) {
      throw new AccessDeniedException("A verified JWT identity is required");
    }
    UUID scopedCustomerId;
    if (hasPrivilegedRole(auth)) {
      if (customerId == null)
        throw new IllegalArgumentException("customerId is required for operations users");
      scopedCustomerId = customerId;
    } else if (auth.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"))) {
      String claim = jwt.getToken().getClaimAsString("customer_id");
      if (claim == null) throw new AccessDeniedException("Customer identity is missing");
      scopedCustomerId = UUID.fromString(claim);
    } else {
      throw new AccessDeniedException("Account listing is not permitted for this role");
    }
    return service.listForCustomer(scopedCustomerId);
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
