package com.enterprise.payment.outbox.application;

import com.enterprise.payment.outbox.infrastructure.*;
import com.enterprise.payment.shared.error.ResourceNotFoundException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {
  private final OutboxEventRepository repository;

  @Value("${app.outbox.lease-duration-seconds:300}")
  private long leaseSeconds;

  public OutboxService(OutboxEventRepository r) {
    repository = r;
  }

  @Transactional
  public List<OutboxEventEntity> claim(int batch) {
    var rows = repository.lockNextBatch(batch);
    rows.forEach(e -> e.markProcessing(leaseSeconds, "outbox-publisher"));
    return rows;
  }

  @Transactional
  public boolean published(UUID id, UUID lease) {
    var e = repository.findById(id).orElse(null);
    if (e == null || !e.isLease(lease)) return false;
    e.markPublished();
    return true;
  }

  @Transactional
  public boolean failed(UUID id, UUID lease, String error, int max) {
    var e = repository.findById(id).orElse(null);
    if (e == null || !e.isLease(lease)) return false;
    e.markFailed(error, e.getAttempts() >= max);
    return true;
  }

  @Transactional
  public OutboxEventEntity replay(UUID id) {
    var e = get(id);
    e.replay();
    return e;
  }

  @Transactional(readOnly = true)
  public Page<OutboxEventEntity> list(Pageable p) {
    return repository.findAll(p);
  }

  @Transactional(readOnly = true)
  public OutboxEventEntity get(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(
            () ->
                new ResourceNotFoundException("OUTBOX_EVENT_NOT_FOUND", "Outbox event not found"));
  }
}
