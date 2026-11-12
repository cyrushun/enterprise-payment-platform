package com.enterprise.payment.payment.domain;

public class TransferConflictException extends RuntimeException {
  public TransferConflictException(String message) {
    super(message);
  }
}
