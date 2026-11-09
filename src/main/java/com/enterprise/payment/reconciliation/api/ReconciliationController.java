package com.enterprise.payment.reconciliation.api;

import com.enterprise.payment.reconciliation.application.ReconciliationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.reconciliation.enabled",
    havingValue = "true",
    matchIfMissing = true)
@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {
  private final JdbcTemplate jdbc;
  private final ReconciliationService service;

  public ReconciliationController(JdbcTemplate jdbc, ReconciliationService service) {
    this.jdbc = jdbc;
    this.service = service;
  }

  @GetMapping("/runs")
  public List<Map<String, Object>> runs() {
    return jdbc.queryForList(
        "select id,status,started_at,completed_at,accounts_checked,discrepancies_found,error_message from reconciliation_runs order by started_at desc limit 100");
  }

  @GetMapping("/runs/{runId}/discrepancies")
  public List<Map<String, Object>> discrepancies(@PathVariable UUID runId) {
    return jdbc.queryForList(
        "select id,run_id,discrepancy_type,resource_type,resource_id,expected_value,actual_value,status,detected_at from reconciliation_discrepancies where run_id=? order by detected_at",
        runId);
  }

  @PostMapping("/run")
  public ReconciliationService.ReconciliationResult runNow() {
    return service.runNow();
  }
}
