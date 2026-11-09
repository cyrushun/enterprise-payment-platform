package com.enterprise.payment.payment.api;

import com.enterprise.payment.payment.application.TransferService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class TransferController {
  private final TransferService transfers;
  private final com.enterprise.payment.security.OwnershipService ownership;

  public TransferController(
      TransferService transfers, com.enterprise.payment.security.OwnershipService ownership) {
    this.transfers = transfers;
    this.ownership = ownership;
  }

  @PostMapping("/transfers")
  public ResponseEntity<TransferResponse> create(
      @Valid @RequestBody CreateTransferRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      Authentication auth) {
    requireAccountOwner(auth, request.sourceAccountId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(transfers.create(request, idempotencyKey));
  }

  @GetMapping("/transfers/{id}")
  public TransferResponse get(@PathVariable UUID id, Authentication auth) {
    TransferResponse response = transfers.get(id);
    requireTransferOwner(auth, id);
    return response;
  }

  @GetMapping("/transfers/reference/{reference}")
  public TransferResponse getByReference(@PathVariable String reference, Authentication auth) {
    TransferResponse response = transfers.getByReference(reference);
    requireTransferOwner(auth, response.id());
    return response;
  }

  @GetMapping("/accounts/{accountId}/transfers")
  public Page<TransferResponse> history(
      @PathVariable UUID accountId,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      Authentication auth) {
    requireAccountOwner(auth, accountId);
    return transfers.history(accountId, pageable);
  }

  private void requireAccountOwner(Authentication auth, UUID accountId) {
    if (auth == null || !auth.isAuthenticated() || privileged(auth)) return;
    if (auth instanceof JwtAuthenticationToken jwt) {
      String cid = jwt.getToken().getClaimAsString("customer_id");
      if (cid == null) throw new AccessDeniedException("Customer identity is missing");
      ownership.requireAccountOwner(accountId, UUID.fromString(cid));
    }
  }

  private void requireTransferOwner(Authentication auth, UUID id) {
    if (auth == null || !auth.isAuthenticated() || privileged(auth)) return;
    if (auth instanceof JwtAuthenticationToken jwt) {
      String cid = jwt.getToken().getClaimAsString("customer_id");
      if (cid == null) throw new AccessDeniedException("Customer identity is missing");
      ownership.requireTransferAccountOwnership(id, UUID.fromString(cid));
    }
  }

  private boolean privileged(Authentication auth) {
    return auth.getAuthorities().stream()
        .anyMatch(
            a ->
                a.getAuthority().equals("ROLE_OPERATIONS")
                    || a.getAuthority().equals("ROLE_ADMIN"));
  }
}
