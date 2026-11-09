package com.enterprise.payment.outbox.domain;

/** Raised when an outbox operation is invalid for the event's current lifecycle state. */
public class InvalidOutboxStateException extends RuntimeException {
  public InvalidOutboxStateException(String message) {
    super(message);
  }
}
