package com.enterprise.payment.payment.domain;

public class InvalidTransferException extends RuntimeException {
  public InvalidTransferException(String message) {
    super(message);
  }
}
