package com.enterprise.payment.audit.api;

import com.enterprise.payment.audit.infrastructure.AuditLogRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {
  private final AuditLogRepository repository;

  public AuditLogController(AuditLogRepository r) {
    repository = r;
  }

  @GetMapping
  public Page<AuditLogResponse> list(Pageable p) {
    return repository.findAll(p).map(AuditLogResponse::from);
  }

  @GetMapping("/{id}")
  public AuditLogResponse get(@PathVariable UUID id) {
    return repository
        .findById(id)
        .map(AuditLogResponse::from)
        .orElseThrow(
            () ->
                new com.enterprise.payment.shared.error.ResourceNotFoundException(
                    "AUDIT_LOG_NOT_FOUND", "Audit log not found"));
  }
}
