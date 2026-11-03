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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class TransferController {
  private final TransferService transfers;

  public TransferController(TransferService transfers) {
    this.transfers = transfers;
  }

  @PostMapping("/transfers")
  public ResponseEntity<TransferResponse> create(
      @Valid @RequestBody CreateTransferRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(transfers.create(request, idempotencyKey));
  }

  @GetMapping("/transfers/{id}")
  public TransferResponse get(@PathVariable UUID id) {
    return transfers.get(id);
  }

  @GetMapping("/transfers/reference/{reference}")
  public TransferResponse getByReference(@PathVariable String reference) {
    return transfers.getByReference(reference);
  }

  @GetMapping("/accounts/{accountId}/transfers")
  public Page<TransferResponse> history(
      @PathVariable UUID accountId,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return transfers.history(accountId, pageable);
  }
}
