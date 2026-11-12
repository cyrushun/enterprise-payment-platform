package com.enterprise.payment.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class RedisRateLimitStoreIntegrationTest {
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
  private static LettuceConnectionFactory connectionFactory;
  private static RedisRateLimitStore store;

  @BeforeAll
  static void startRedis() {
    REDIS.start();
    connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
    template.afterPropertiesSet();
    store = new RedisRateLimitStore(template);
  }

  @AfterAll
  static void stopRedis() {
    if (connectionFactory != null) connectionFactory.destroy();
    REDIS.stop();
  }

  @Test
  void incrementsAtomicallyAndSetsAnExpiry() {
    String key = "test:ratelimit:" + java.util.UUID.randomUUID();
    RateLimitDecision first = store.consume(key, Duration.ofSeconds(30));
    RateLimitDecision second = store.consume(key, Duration.ofSeconds(30));

    assertThat(first.count()).isEqualTo(1);
    assertThat(second.count()).isEqualTo(2);
    assertThat(second.retryAfterSeconds()).isBetween(1L, 30L);
  }
}
