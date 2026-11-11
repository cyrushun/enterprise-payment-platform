package com.enterprise.payment.security.ratelimit;

import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisRateLimitStore implements RateLimitStore {
  private static final DefaultRedisScript<String> INCREMENT_SCRIPT =
      new DefaultRedisScript<>(
          "local current = redis.call('INCR', KEYS[1]); "
              + "if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; "
              + "local ttl = redis.call('PTTL', KEYS[1]); "
              + "return tostring(current) .. '|' .. tostring(ttl)",
          String.class);

  private final StringRedisTemplate redis;

  public RedisRateLimitStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public RateLimitDecision consume(String key, Duration window) {
    long windowMillis = Math.max(1L, window.toMillis());
    String result =
        redis.execute(INCREMENT_SCRIPT, java.util.List.of(key), Long.toString(windowMillis));
    if (result == null)
      throw new IllegalStateException("Redis returned an empty rate-limit result");
    String[] values = result.split("\\|", -1);
    if (values.length != 2)
      throw new IllegalStateException("Redis returned an invalid rate-limit result");
    long count = Long.parseLong(values[0]);
    long ttlMillis = Math.max(0L, Long.parseLong(values[1]));
    long retryAfter = Math.max(1L, (ttlMillis + 999L) / 1000L);
    long reset = Instant.now().getEpochSecond() + retryAfter;
    return new RateLimitDecision(count, 0L, retryAfter, reset);
  }
}
