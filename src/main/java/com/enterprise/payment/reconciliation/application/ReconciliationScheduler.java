package com.enterprise.payment.reconciliation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled boundary; manual API execution invokes ReconciliationService directly. */
@Component
@ConditionalOnProperty(
    name = "app.reconciliation.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ReconciliationScheduler {
  private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);
  private final ReconciliationService service;

  public ReconciliationScheduler(ReconciliationService service) {
    this.service = service;
  }

  @Scheduled(cron = "${app.reconciliation.cron:0 15 2 * * *}")
  public void reconcile() {
    try {
      service.runNow();
    } catch (RuntimeException ex) {
      log.error("Scheduled reconciliation failed", ex);
    }
  }
}
