package com.enterprise.payment.reconciliation.application;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only reconciliation. Findings are persisted; no balance is automatically repaired. */
@Service
public class ReconciliationService {
  private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
  private final JdbcTemplate jdbc;

  public ReconciliationService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public ReconciliationResult runNow() {
    UUID runId = UUID.randomUUID();
    Instant started = Instant.now();
    jdbc.update(
        "insert into reconciliation_runs(id,status,started_at) values (?, 'RUNNING', ?)",
        runId,
        Timestamp.from(started));
    long checked = 0;
    long discrepancies = 0;
    try {
      var accountRows =
          jdbc.query(
              "select a.id,a.balance,coalesce(sum(case when e.entry_type='CREDIT' then e.amount else -e.amount end),0) ledger_balance "
                  + "from accounts a left join ledger_entries e on e.account_id=a.id and e.currency=a.currency group by a.id,a.balance",
              (rs, n) ->
                  new AccountRow(
                      rs.getObject(1, UUID.class), rs.getBigDecimal(2), rs.getBigDecimal(3)));
      for (var row : accountRows) {
        checked++;
        if (row.balance.compareTo(row.ledger) != 0) {
          persist(
              runId,
              "ACCOUNT_LEDGER_BALANCE",
              "ACCOUNT",
              row.id,
              row.ledger.toPlainString(),
              row.balance.toPlainString());
          discrepancies++;
        }
      }

      var journalRows =
          jdbc.query(
              "select lt.id, coalesce(sum(case when le.entry_type='DEBIT' then le.amount else 0 end),0) debit, "
                  + "coalesce(sum(case when le.entry_type='CREDIT' then le.amount else 0 end),0) credit "
                  + "from ledger_transactions lt left join ledger_entries le on le.ledger_transaction_id=lt.id group by lt.id",
              (rs, n) ->
                  new JournalRow(
                      rs.getObject(1, UUID.class), rs.getBigDecimal(2), rs.getBigDecimal(3)));
      for (var row : journalRows) {
        if (row.debit.compareTo(row.credit) != 0) {
          persist(
              runId,
              "UNBALANCED_JOURNAL",
              "LEDGER_TRANSACTION",
              row.id,
              row.debit.toPlainString(),
              row.credit.toPlainString());
          discrepancies++;
        }
      }

      var transferRows =
          jdbc.query(
              "select t.id,t.amount,coalesce(sum(case when le.entry_type='DEBIT' then le.amount else 0 end),0) debit_amount, "
                  + "coalesce(sum(case when le.entry_type='CREDIT' then le.amount else 0 end),0) credit_amount "
                  + "from transfers t left join ledger_transactions lt on lt.transfer_id=t.id "
                  + "left join ledger_entries le on le.ledger_transaction_id=lt.id group by t.id,t.amount",
              (rs, n) ->
                  new TransferRow(
                      rs.getObject(1, UUID.class),
                      rs.getBigDecimal(2),
                      rs.getBigDecimal(3),
                      rs.getBigDecimal(4)));
      for (var row : transferRows) {
        if (row.amount.compareTo(row.debit) != 0 || row.amount.compareTo(row.credit) != 0) {
          persist(
              runId,
              "TRANSFER_LEDGER_AMOUNT",
              "TRANSFER",
              row.id,
              row.amount.toPlainString(),
              "debit=" + row.debit.toPlainString() + ",credit=" + row.credit.toPlainString());
          discrepancies++;
        }
      }

      jdbc.update(
          "update reconciliation_runs set status='COMPLETED',completed_at=?,accounts_checked=?,"
              + "discrepancies_found=? where id=?",
          Timestamp.from(Instant.now()),
          checked,
          discrepancies,
          runId);
      if (discrepancies > 0) {
        log.error("Reconciliation found {} discrepancy records runId={}", discrepancies, runId);
      } else {
        log.info("Reconciliation completed cleanly scanned={} runId={}", checked, runId);
      }
      return new ReconciliationResult(runId, "COMPLETED", checked, discrepancies);
    } catch (Exception ex) {
      String message = String.valueOf(ex.getMessage());
      try {
        jdbc.update(
            "update reconciliation_runs set status='FAILED',completed_at=?,accounts_checked=?,"
                + "discrepancies_found=?,error_message=? where id=?",
            Timestamp.from(Instant.now()),
            checked,
            discrepancies,
            message.substring(0, Math.min(1800, message.length())),
            runId);
      } catch (Exception persistenceFailure) {
        ex.addSuppressed(persistenceFailure);
      }
      log.error("Reconciliation failed runId={}", runId, ex);
      throw new ReconciliationExecutionException(runId, ex);
    }
  }

  private void persist(
      UUID run, String type, String resource, UUID id, String expected, String actual) {
    jdbc.update(
        "insert into reconciliation_discrepancies(id,run_id,discrepancy_type,resource_type,resource_id,"
            + "expected_value,actual_value) values (?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        run,
        type,
        resource,
        id,
        expected,
        actual);
  }

  public record ReconciliationResult(
      UUID runId, String status, long accountsChecked, long discrepanciesFound) {}

  private record AccountRow(UUID id, BigDecimal balance, BigDecimal ledger) {}

  private record JournalRow(UUID id, BigDecimal debit, BigDecimal credit) {}

  private record TransferRow(UUID id, BigDecimal amount, BigDecimal debit, BigDecimal credit) {}
}
