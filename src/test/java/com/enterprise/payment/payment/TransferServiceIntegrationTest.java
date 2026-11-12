package com.enterprise.payment.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.payment.account.domain.AccountType;
import com.enterprise.payment.account.infrastructure.AccountEntity;
import com.enterprise.payment.account.infrastructure.AccountRepository;
import com.enterprise.payment.customer.infrastructure.CustomerEntity;
import com.enterprise.payment.customer.infrastructure.CustomerRepository;
import com.enterprise.payment.ledger.infrastructure.LedgerEntryRepository;
import com.enterprise.payment.ledger.infrastructure.LedgerTransactionRepository;
import com.enterprise.payment.outbox.infrastructure.OutboxEventRepository;
import com.enterprise.payment.payment.api.CreateTransferRequest;
import com.enterprise.payment.payment.application.TransferService;
import com.enterprise.payment.payment.domain.InsufficientBalanceException;
import com.enterprise.payment.payment.domain.TransferConflictException;
import com.enterprise.payment.payment.infrastructure.IdempotencyRecordRepository;
import com.enterprise.payment.payment.infrastructure.TransferRepository;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "app.outbox.enabled=false",
      "spring.kafka.listener.auto-startup=false",
      "spring.kafka.admin.auto-create=false"
    })
class TransferServiceIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("payment_platform")
          .withUsername("payment_app")
          .withPassword("test_password");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired TransferService transferService;
  @Autowired CustomerRepository customers;
  @Autowired AccountRepository accounts;
  @Autowired TransferRepository transfers;
  @Autowired IdempotencyRecordRepository idempotencyRecords;
  @Autowired LedgerTransactionRepository ledgerTransactions;
  @Autowired LedgerEntryRepository ledgerEntries;
  @Autowired OutboxEventRepository outboxEvents;
  @Autowired JdbcTemplate jdbcTemplate;
  private AccountEntity source;
  private AccountEntity destination;

  @BeforeEach
  void setUp() {
    outboxEvents.deleteAll();
    idempotencyRecords.deleteAll();
    ledgerEntries.deleteAll();
    ledgerTransactions.deleteAll();
    transfers.deleteAll();
    accounts.deleteAll();
    // Do not delete all customers: V6 seeds OPERATIONS/ADMIN users whose
    // users.customer_id foreign keys intentionally reference their customers.
    // Each test creates a unique customer, so old test customers can safely remain.
    CustomerEntity customer =
        customers.save(
            new CustomerEntity(
                "Alex", "Tan", "alex." + UUID.randomUUID() + "@example.test", "+60123456789"));
    source = new AccountEntity(customer.getId(), "1000000000000001", AccountType.SAVINGS, "MYR");
    destination =
        new AccountEntity(customer.getId(), "1000000000000002", AccountType.SAVINGS, "MYR");
    source.credit(new BigDecimal("1000.0000"));
    source = accounts.save(source);
    destination = accounts.save(destination);
  }

  @Test
  void successfulTransferPostsLedgerAndUpdatesBothBalances() {
    var result = transferService.create(request("250.0000"), "success-001");
    assertThat(result.status().name()).isEqualTo("COMPLETED");
    assertThat(accounts.findById(source.getId()).orElseThrow().getBalance())
        .isEqualByComparingTo("750.0000");
    assertThat(accounts.findById(destination.getId()).orElseThrow().getBalance())
        .isEqualByComparingTo("250.0000");
    assertThat(transfers.count()).isEqualTo(1);
    assertThat(ledgerTransactions.count()).isEqualTo(1);
    assertThat(ledgerEntries.count()).isEqualTo(2);
    assertThat(idempotencyRecords.count()).isEqualTo(1);
    assertThat(outboxEvents.count()).isEqualTo(1);
    Integer unbalanced =
        jdbcTemplate.queryForObject(
            """
                select count(*) from (
                    select lt.id
                    from ledger_transactions lt join ledger_entries le on le.ledger_transaction_id = lt.id
                    group by lt.id
                    having sum(case when le.entry_type = 'DEBIT' then le.amount else 0 end)
                        <> sum(case when le.entry_type = 'CREDIT' then le.amount else 0 end)
                ) journals
                """,
            Integer.class);
    assertThat(unbalanced).isZero();
  }

  @Test
  void identicalIdempotentReplayReturnsOriginalTransfer() {
    var first = transferService.create(request("125.0000"), "replay-001");
    var replay = transferService.create(request("125.0000"), "replay-001");
    assertThat(replay.id()).isEqualTo(first.id());
    assertThat(transfers.count()).isEqualTo(1);
    assertThat(outboxEvents.count()).isEqualTo(1);
    assertThat(accounts.findById(source.getId()).orElseThrow().getBalance())
        .isEqualByComparingTo("875.0000");
  }

  @Test
  void concurrentRequestsWithSameKeyExecuteOnlyOnce() throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<UUID> first = executor.submit(() -> concurrentReplay(ready, start));
      Future<UUID> second = executor.submit(() -> concurrentReplay(ready, start));
      ready.await();
      start.countDown();
      assertThat(first.get()).isEqualTo(second.get());
    }
    assertThat(transfers.count()).isEqualTo(1);
    assertThat(ledgerTransactions.count()).isEqualTo(1);
    assertThat(ledgerEntries.count()).isEqualTo(2);
    assertThat(outboxEvents.count()).isEqualTo(1);
    assertThat(accounts.findById(source.getId()).orElseThrow().getBalance())
        .isEqualByComparingTo("875.0000");
  }

  private UUID concurrentReplay(CountDownLatch ready, CountDownLatch start) throws Exception {
    ready.countDown();
    start.await();
    return transferService.create(request("125.0000"), "concurrent-same-key").id();
  }

  @Test
  void sameIdempotencyKeyWithDifferentPayloadIsRejected() {
    transferService.create(request("125.0000"), "conflict-001");
    assertThatThrownBy(() -> transferService.create(request("126.0000"), "conflict-001"))
        .isInstanceOf(TransferConflictException.class);
    assertThat(transfers.count()).isEqualTo(1);
  }

  @Test
  void insufficientFundsRollsBackAllFinancialWrites() {
    assertThatThrownBy(() -> transferService.create(request("1000.0001"), "insufficient-001"))
        .isInstanceOf(InsufficientBalanceException.class);
    assertThat(accounts.findById(source.getId()).orElseThrow().getBalance())
        .isEqualByComparingTo("1000.0000");
    assertThat(accounts.findById(destination.getId()).orElseThrow().getBalance())
        .isEqualByComparingTo("0.0000");
    assertThat(transfers.count()).isZero();
    assertThat(ledgerTransactions.count()).isZero();
    assertThat(ledgerEntries.count()).isZero();
    assertThat(idempotencyRecords.count()).isZero();
    assertThat(outboxEvents.count()).isZero();
  }

  @Test
  void concurrentTransfersCannotOverdrawSourceAccount() throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Boolean> first =
          executor.submit(() -> attempt("800.0000", "concurrent-A", ready, start));
      Future<Boolean> second =
          executor.submit(() -> attempt("700.0000", "concurrent-B", ready, start));
      ready.await();
      start.countDown();
      int successes = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
      assertThat(successes).isEqualTo(1);
    }
    BigDecimal finalBalance = accounts.findById(source.getId()).orElseThrow().getBalance();
    assertThat(finalBalance).isIn(new BigDecimal("200.0000"), new BigDecimal("300.0000"));
    assertThat(transfers.count()).isEqualTo(1);
    assertThat(ledgerEntries.count()).isEqualTo(2);
    assertThat(outboxEvents.count()).isEqualTo(1);
  }

  private boolean attempt(String amount, String key, CountDownLatch ready, CountDownLatch start)
      throws Exception {
    ready.countDown();
    start.await();
    try {
      transferService.create(request(amount), key);
      return true;
    } catch (InsufficientBalanceException expected) {
      return false;
    }
  }

  private CreateTransferRequest request(String amount) {
    return new CreateTransferRequest(
        source.getId(),
        destination.getId(),
        new BigDecimal(amount),
        "MYR",
        "Integration test transfer");
  }
}
