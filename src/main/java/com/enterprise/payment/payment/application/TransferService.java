package com.enterprise.payment.payment.application;

import com.enterprise.payment.account.infrastructure.AccountEntity;
import com.enterprise.payment.account.infrastructure.AccountRepository;
import com.enterprise.payment.audit.application.AuditService;
import com.enterprise.payment.ledger.domain.EntryType;
import com.enterprise.payment.ledger.domain.JournalLine;
import com.enterprise.payment.ledger.domain.JournalValidator;
import com.enterprise.payment.ledger.infrastructure.*;
import com.enterprise.payment.messaging.event.TransferCompletedEvent;
import com.enterprise.payment.outbox.infrastructure.OutboxEventEntity;
import com.enterprise.payment.outbox.infrastructure.OutboxEventRepository;
import com.enterprise.payment.payment.api.CreateTransferRequest;
import com.enterprise.payment.payment.api.TransferResponse;
import com.enterprise.payment.payment.domain.*;
import com.enterprise.payment.payment.infrastructure.*;
import com.enterprise.payment.shared.error.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {
  private static final String IDEMPOTENCY_SCOPE = "TRANSFER_CREATE";
  private final AccountRepository accounts;
  private final TransferRepository transfers;
  private final IdempotencyRecordRepository idempotencyRecords;
  private final LedgerTransactionRepository ledgerTransactions;
  private final LedgerEntryRepository ledgerEntries;
  private final EntityManager entityManager;
  private final OutboxEventRepository outboxEvents;
  private final ObjectMapper objectMapper;
  private final AuditService auditService;
  private final io.micrometer.core.instrument.MeterRegistry metrics;
  private final io.micrometer.tracing.Tracer tracer;

  @Value("${app.kafka.transfer-topic:payment.transfer-events}")
  private String transferTopic;

  public TransferService(
      AccountRepository accounts,
      TransferRepository transfers,
      IdempotencyRecordRepository idempotencyRecords,
      LedgerTransactionRepository ledgerTransactions,
      LedgerEntryRepository ledgerEntries,
      EntityManager entityManager,
      OutboxEventRepository outboxEvents,
      ObjectMapper objectMapper,
      AuditService auditService,
      io.micrometer.core.instrument.MeterRegistry metrics,
      io.micrometer.tracing.Tracer tracer) {
    this.accounts = accounts;
    this.transfers = transfers;
    this.idempotencyRecords = idempotencyRecords;
    this.ledgerTransactions = ledgerTransactions;
    this.ledgerEntries = ledgerEntries;
    this.entityManager = entityManager;
    this.outboxEvents = outboxEvents;
    this.objectMapper = objectMapper;
    this.auditService = auditService;
    this.metrics = metrics;
    this.tracer = tracer;
  }

  @Transactional
  public TransferResponse create(CreateTransferRequest request, String idempotencyKey) {
    long operationStartedNanos = System.nanoTime();
    validateRequest(request, idempotencyKey);
    String requestHash = hash(request);

    // A transaction-scoped PostgreSQL advisory lock serializes requests using the same key.
    // The unique constraint remains the final database-level guard.
    String lockName = IDEMPOTENCY_SCOPE + ":" + idempotencyKey;
    entityManager
        .createNativeQuery(
            "select 1 from (select pg_advisory_xact_lock(hashtextextended(:lockName, 0))) lock_result")
        .setParameter("lockName", lockName)
        .getSingleResult();

    Optional<IdempotencyRecordEntity> previous =
        idempotencyRecords.findByOperationScopeAndIdempotencyKey(IDEMPOTENCY_SCOPE, idempotencyKey);
    if (previous.isPresent()) {
      IdempotencyRecordEntity record = previous.get();
      if (!record.getRequestHash().equals(requestHash))
        throw new TransferConflictException(
            "Idempotency-Key was already used with a different request payload");
      TransferEntity original =
          transfers
              .findById(record.getResourceId())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Idempotency record references a missing transfer"));
      return toResponse(original);
    }

    List<AccountEntity> locked =
        accounts.findAllByIdForUpdate(
            List.of(request.sourceAccountId(), request.destinationAccountId()));
    if (locked.size() != 2)
      throw new ResourceNotFoundException(
          "ACCOUNT_NOT_FOUND", "Source or destination account not found");
    Map<UUID, AccountEntity> byId = new HashMap<>();
    locked.forEach(a -> byId.put(a.getId(), a));
    AccountEntity source = byId.get(request.sourceAccountId());
    AccountEntity destination = byId.get(request.destinationAccountId());

    validateAccounts(source, destination, request);
    if (source.getBalance().compareTo(request.amount()) < 0)
      throw new InsufficientBalanceException(source.getBalance(), request.amount());

    String reference = generateReference();
    TransferEntity transfer =
        transfers.save(
            new TransferEntity(
                source.getId(),
                destination.getId(),
                request.amount(),
                request.currency(),
                reference));

    source.debit(request.amount());
    destination.credit(request.amount());

    List<JournalLine> journal =
        List.of(
            new JournalLine(source.getId(), EntryType.DEBIT, request.amount(), request.currency()),
            new JournalLine(
                destination.getId(), EntryType.CREDIT, request.amount(), request.currency()));
    JournalValidator.validate(journal);
    LedgerTransactionEntity ledger =
        ledgerTransactions.save(
            new LedgerTransactionEntity(transfer.getId(), request.description()));
    ledgerEntries.saveAll(
        List.of(
            new LedgerEntryEntity(
                ledger.getId(),
                source.getId(),
                EntryType.DEBIT,
                request.amount(),
                request.currency()),
            new LedgerEntryEntity(
                ledger.getId(),
                destination.getId(),
                EntryType.CREDIT,
                request.amount(),
                request.currency())));
    idempotencyRecords.save(
        new IdempotencyRecordEntity(
            IDEMPOTENCY_SCOPE, idempotencyKey, requestHash, transfer.getId()));
    UUID eventId = UUID.randomUUID();
    TransferCompletedEvent event =
        new TransferCompletedEvent(
            eventId,
            "TransferCompleted",
            Instant.now(),
            transfer.getId(),
            transfer.getReference(),
            source.getId(),
            destination.getId(),
            transfer.getAmount(),
            transfer.getCurrency());
    outboxEvents.save(
        new OutboxEventEntity(
            eventId,
            transfer.getId(),
            event.eventType(),
            transferTopic,
            transfer.getId().toString(),
            objectMapper.valueToTree(event),
            org.slf4j.MDC.get("correlationId"),
            buildTraceparent(),
            buildTracestate()));
    UUID actorId = null;
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
      try {
        actorId = UUID.fromString(jwtAuth.getToken().getSubject());
      } catch (IllegalArgumentException ignored) {
        /* non-UUID subject */
      }
    }
    JsonNode auditDetails =
        objectMapper.valueToTree(
            Map.of(
                "amount",
                request.amount(),
                "currency",
                request.currency(),
                "sourceAccountId",
                source.getId(),
                "destinationAccountId",
                destination.getId()));
    auditService.record(
        actorId,
        "TRANSFER_CREATED",
        "TRANSFER",
        transfer.getId(),
        org.slf4j.MDC.get("correlationId"),
        auditDetails);
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              @Override
              public void afterCommit() {
                metrics.counter("payment.transfers.completed").increment();
                metrics
                    .timer("payment.transfers.duration")
                    .record(
                        System.nanoTime() - operationStartedNanos,
                        java.util.concurrent.TimeUnit.NANOSECONDS);
              }
            });
    return toResponse(transfer);
  }

  @Transactional(readOnly = true)
  public TransferResponse get(UUID id) {
    return transfers
        .findById(id)
        .map(this::toResponse)
        .orElseThrow(
            () -> new ResourceNotFoundException("TRANSFER_NOT_FOUND", "Transfer not found"));
  }

  @Transactional(readOnly = true)
  public TransferResponse getByReference(String reference) {
    return transfers
        .findByReference(reference)
        .map(this::toResponse)
        .orElseThrow(
            () -> new ResourceNotFoundException("TRANSFER_NOT_FOUND", "Transfer not found"));
  }

  @Transactional(readOnly = true)
  public Page<TransferResponse> history(UUID accountId, Pageable pageable) {
    if (!accounts.existsById(accountId))
      throw new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Account not found");
    return transfers
        .findByFromAccountIdOrToAccountId(accountId, accountId, pageable)
        .map(this::toResponse);
  }

  private String buildTraceparent() {
    io.micrometer.tracing.Span currentSpan = tracer.currentSpan();
    if (currentSpan == null) return null;
    io.micrometer.tracing.TraceContext context = currentSpan.context();
    String traceId = context.traceId();
    String spanId = context.spanId();
    if (traceId == null
        || !traceId.matches("[0-9a-f]{32}")
        || spanId == null
        || !spanId.matches("[0-9a-f]{16}")) return null;
    return "00-"
        + traceId
        + "-"
        + spanId
        + "-"
        + (Boolean.TRUE.equals(context.sampled()) ? "01" : "00");
  }

  private String buildTracestate() {
    // Micrometer's TraceContext does not expose W3C tracestate. Read it from
    // the OpenTelemetry context used by the Micrometer OTel bridge.
    io.opentelemetry.api.trace.SpanContext context =
        io.opentelemetry.api.trace.Span.current().getSpanContext();
    if (!context.isValid()) return null;
    String tracestate = context.getTraceState().toString();
    return tracestate == null || tracestate.isBlank() ? null : tracestate;
  }

  private void validateRequest(CreateTransferRequest request, String key) {
    if (request.sourceAccountId().equals(request.destinationAccountId()))
      throw new InvalidTransferException("Source and destination accounts must be different");
    if (key == null || key.isBlank() || key.length() > 128)
      throw new InvalidTransferException(
          "A valid Idempotency-Key header (1-128 characters) is required");
    if (request.amount() == null || request.amount().signum() <= 0)
      throw new InvalidTransferException("Transfer amount must be greater than zero");
    if (request.amount().scale() > 4 || request.amount().precision() > 19)
      throw new InvalidTransferException("Amount must fit NUMERIC(19,4)");
    if (request.currency() == null || !request.currency().matches("[A-Z]{3}"))
      throw new InvalidTransferException("Currency must be a 3-letter uppercase code");
  }

  private void validateAccounts(
      AccountEntity source, AccountEntity destination, CreateTransferRequest request) {
    if (source.getStatus() != com.enterprise.payment.account.domain.AccountStatus.ACTIVE)
      throw new InvalidTransferException("Source account is not ACTIVE");
    if (destination.getStatus() != com.enterprise.payment.account.domain.AccountStatus.ACTIVE)
      throw new InvalidTransferException("Destination account is not ACTIVE");
    if (!source.getCurrency().equals(request.currency())
        || !destination.getCurrency().equals(request.currency()))
      throw new InvalidTransferException("Transfer currency must match both account currencies");
  }

  private String hash(CreateTransferRequest r) {
    String description = r.description();
    String canonical =
        r.sourceAccountId()
            + "|"
            + r.destinationAccountId()
            + "|"
            + r.amount().stripTrailingZeros().toPlainString()
            + "|"
            + r.currency()
            + "|"
            + (description == null ? "-1:" : description.length() + ":" + description);
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private String generateReference() {
    String date =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(Instant.now());
    return "TRF-"
        + date
        + "-"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
  }

  private TransferResponse toResponse(TransferEntity t) {
    return new TransferResponse(
        t.getId(),
        t.getReference(),
        t.getFromAccountId(),
        t.getToAccountId(),
        t.getAmount(),
        t.getCurrency(),
        t.getStatus(),
        t.getCreatedAt());
  }
}
