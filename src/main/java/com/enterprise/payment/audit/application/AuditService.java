package com.enterprise.payment.audit.application;

import com.enterprise.payment.audit.infrastructure.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
  private final AuditLogRepository repository;

  public AuditService(AuditLogRepository r) {
    repository = r;
  }

  @Transactional
  public void record(
      UUID actor,
      String action,
      String resourceType,
      UUID resourceId,
      String correlationId,
      JsonNode details) {
    repository.save(
        new AuditLogEntity(actor, action, resourceType, resourceId, correlationId, details));
  }
}
