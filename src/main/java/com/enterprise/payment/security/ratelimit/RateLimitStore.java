package com.enterprise.payment.security.ratelimit;

import java.time.Duration;

public interface RateLimitStore {
  RateLimitDecision consume(String key, Duration window);
}
