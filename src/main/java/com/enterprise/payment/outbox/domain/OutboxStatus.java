package com.enterprise.payment.outbox.domain;

public enum OutboxStatus {
  PENDING,
  PROCESSING,
  RETRY,
  PUBLISHED,
  DEAD
}
