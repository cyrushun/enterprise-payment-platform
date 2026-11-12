package com.enterprise.payment.shared.error;

public class DependencyUnavailableException extends RuntimeException {
  public DependencyUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
