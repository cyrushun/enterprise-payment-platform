package com.enterprise.payment.outbox.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {
  private final JdbcTemplate jdbc;
  private final AtomicLong pending = new AtomicLong();
  private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();

  public OutboxMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
    this.jdbc = jdbc;
    Gauge.builder("payment.outbox.pending", pending, AtomicLong::get).register(registry);
    Gauge.builder(
            "payment.outbox.oldest.pending.age.seconds", oldestPendingAgeSeconds, AtomicLong::get)
        .register(registry);
  }

  @Scheduled(fixedDelayString = "${app.observability.outbox-gauge-refresh-ms:30000}")
  public void refresh() {
    try {
      Long count =
          jdbc.queryForObject(
              "select count(*) from outbox_events where status in ('PENDING','RETRY','PROCESSING')",
              Long.class);
      Long age =
          jdbc.queryForObject(
              "select coalesce(extract(epoch from (now() - min(created_at)))::bigint, 0) from outbox_events where status in ('PENDING','RETRY','PROCESSING')",
              Long.class);
      pending.set(count == null ? 0 : count);
      oldestPendingAgeSeconds.set(age == null ? 0 : age);
    } catch (Exception ignored) {
      // Metrics are best-effort and must never affect payment processing.
    }
  }
}
