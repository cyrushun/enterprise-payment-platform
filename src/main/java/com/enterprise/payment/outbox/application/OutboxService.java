package com.enterprise.payment.outbox.application;

import com.enterprise.payment.outbox.infrastructure.*;
import com.enterprise.payment.shared.error.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {
  private final OutboxEventRepository repository;

  public OutboxService(OutboxEventRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public List<OutboxEventEntity> claim(int batchSize) {
    List<OutboxEventEntity> rows = repository.lockNextBatch(batchSize);
    rows.forEach(OutboxEventEntity::markProcessing);
    return rows;
  }

  @Transactional
  public void published(UUID id) {
    repository.findById(id).ifPresent(OutboxEventEntity::markPublished);
  }

  @Transactional
  public void failed(UUID id, String error, int maxAttempts) {
    repository.findById(id).ifPresent(e -> e.markFailed(error, e.getAttempts() >= maxAttempts));
  }

  @Transactional(readOnly = true)
  public Page<OutboxEventEntity> list(Pageable pageable) {
    return repository.findAll(pageable);
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
