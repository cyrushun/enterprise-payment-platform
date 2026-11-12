package com.enterprise.payment.shared.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.enterprise.payment.ledger.domain.InvalidJournalException;
import com.enterprise.payment.outbox.domain.InvalidOutboxStateException;
import com.enterprise.payment.reconciliation.application.ReconciliationExecutionException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void accessDeniedMapsToApiError403WithoutLeakingInternalMessage() {
    var response = handler.accessDenied(new AccessDeniedException("private ownership detail"));
    assertEquals(403, response.getStatusCode().value());
    assertEquals("FORBIDDEN", response.getBody().code());
    assertEquals(
        "You do not have permission to access this resource", response.getBody().message());
  }

  @Test
  void invalidCredentialsMapToApiError401() {
    var response =
        handler.statusException(
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    assertEquals(401, response.getStatusCode().value());
    assertEquals("UNAUTHENTICATED", response.getBody().code());
    assertEquals("Invalid credentials", response.getBody().message());
  }

  @Test
  void invalidJournalMapsTo422() {
    var response =
        handler.invalidOperation(new InvalidJournalException("Journal debits must equal credits"));
    assertEquals(422, response.getStatusCode().value());
    assertEquals("INVALID_JOURNAL", response.getBody().code());
  }

  @Test
  void unexpectedFailureDoesNotExposeInternalException() {
    var response =
        handler.unexpected(new IllegalStateException("database password=" + UUID.randomUUID()));
    assertEquals(500, response.getStatusCode().value());
    assertEquals("INTERNAL_ERROR", response.getBody().code());
    assertEquals("An unexpected error occurred", response.getBody().message());
    assertFalse(response.getBody().message().contains("database password"));
  }

  @Test
  void databaseConstraintViolationRemains409Not503() {
    var response = handler.conflict(new DataIntegrityViolationException("unique constraint"));
    assertEquals(409, response.getStatusCode().value());
    assertEquals("RESOURCE_CONFLICT", response.getBody().code());
  }

  @Test
  void dependencyTimeoutMapsTo503WithoutLeakingCause() {
    var response = handler.dependencyUnavailable(new QueryTimeoutException("private SQL details"));
    assertEquals(503, response.getStatusCode().value());
    assertEquals("DEPENDENCY_UNAVAILABLE", response.getBody().code());
    assertEquals("A required service is temporarily unavailable", response.getBody().message());
  }

  @Test
  void sqlProgrammingErrorIsNotMisreportedAsDependencyOutage() {
    var response =
        handler.persistenceFailure(
            new BadSqlGrammarException(
                "query", "select broken", new java.sql.SQLException("syntax")));
    assertEquals(500, response.getStatusCode().value());
    assertEquals("INTERNAL_ERROR", response.getBody().code());
  }

  @Test
  void kafkaDependencyFailureMapsTo503() {
    var response =
        handler.dependencyUnavailable(
            new DependencyUnavailableException(
                "Kafka timeout", new java.util.concurrent.TimeoutException()));
    assertEquals(503, response.getStatusCode().value());
    assertEquals("DEPENDENCY_UNAVAILABLE", response.getBody().code());
  }

  @Test
  void reconciliationExecutionFailureMapsTo503() {
    var response =
        handler.reconciliationFailure(
            new ReconciliationExecutionException(
                UUID.randomUUID(), new QueryTimeoutException("timeout")));
    assertEquals(503, response.getStatusCode().value());
    assertEquals("DEPENDENCY_UNAVAILABLE", response.getBody().code());
  }

  @Test
  void unexpectedReconciliationFailureMapsTo500() {
    var response =
        handler.reconciliationFailure(
            new ReconciliationExecutionException(
                UUID.randomUUID(), new IllegalStateException("internal")));
    assertEquals(500, response.getStatusCode().value());
    assertEquals("INTERNAL_ERROR", response.getBody().code());
  }

  @Test
  void invalidStateMapsToConflict() {
    var response =
        handler.invalidOperationState(
            new InvalidOutboxStateException("Only DEAD events can be replayed"));
    assertEquals(409, response.getStatusCode().value());
    assertEquals("INVALID_STATE", response.getBody().code());
  }
}
