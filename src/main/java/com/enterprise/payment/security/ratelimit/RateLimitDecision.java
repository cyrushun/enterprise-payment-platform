package com.enterprise.payment.security.ratelimit;

public record RateLimitDecision(
    long count, long remaining, long retryAfterSeconds, long resetEpochSeconds) {
  public boolean allowed(long limit) {
    return count <= limit;
  }
}
