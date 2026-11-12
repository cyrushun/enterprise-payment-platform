package com.enterprise.payment.shared.error;

import com.enterprise.payment.account.application.InvalidAccountOperationException;
import com.enterprise.payment.customer.application.DuplicateCustomerEmailException;
import com.enterprise.payment.ledger.domain.InvalidJournalException;
import com.enterprise.payment.outbox.domain.InvalidOutboxStateException;
import com.enterprise.payment.payment.domain.InsufficientBalanceException;
import com.enterprise.payment.payment.domain.InvalidTransferException;
import com.enterprise.payment.payment.domain.TransferConflictException;
import com.enterprise.payment.reconciliation.application.ReconciliationExecutionException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ApiError> statusException(ResponseStatusException ex) {
    HttpStatusCode status = ex.getStatusCode();
    String code =
        switch (status.value()) {
          case 400 -> "BAD_REQUEST";
          case 401 -> "UNAUTHENTICATED";
          case 403 -> "FORBIDDEN";
          case 404 -> "NOT_FOUND";
          case 409 -> "RESOURCE_CONFLICT";
          case 422 -> "UNPROCESSABLE_REQUEST";
          case 429 -> "RATE_LIMITED";
          default -> status.is5xxServerError() ? "REQUEST_FAILED" : "REQUEST_REJECTED";
        };
    String message = ex.getReason();
    if (message == null || message.isBlank()) {
      HttpStatus resolved = HttpStatus.resolve(status.value());
      message = resolved == null ? "Request failed" : resolved.getReasonPhrase();
    }
    if (status.is5xxServerError()) {
      log.error("Request failed with server status {}", status.value(), ex);
      message = "The service could not complete the request";
    }
    return response(status, code, message, List.of());
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ApiError> notFound(ResourceNotFoundException ex) {
    return response(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage(), List.of());
  }

  @ExceptionHandler({
    DuplicateCustomerEmailException.class,
    DataIntegrityViolationException.class,
    TransferConflictException.class
  })
  ResponseEntity<ApiError> conflict(Exception ex) {
    if (ex instanceof TransferConflictException) {
      return response(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", ex.getMessage(), List.of());
    }
    if (ex instanceof DuplicateCustomerEmailException) {
      return response(
          HttpStatus.CONFLICT,
          "DUPLICATE_CUSTOMER_EMAIL",
          "A customer with the supplied email already exists",
          List.of());
    }
    log.warn("Database constraint rejected request", ex);
    return response(
        HttpStatus.CONFLICT,
        "RESOURCE_CONFLICT",
        "The request conflicts with an existing resource",
        List.of());
  }

  @ExceptionHandler(InsufficientBalanceException.class)
  ResponseEntity<ApiError> insufficient(InsufficientBalanceException ex) {
    return response(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "INSUFFICIENT_BALANCE",
        ex.getMessage(),
        List.of(
            "availableBalance=" + ex.getAvailableBalance().toPlainString(),
            "requestedAmount=" + ex.getRequestedAmount().toPlainString()));
  }

  @ExceptionHandler({
    InvalidTransferException.class,
    InvalidAccountOperationException.class,
    InvalidJournalException.class
  })
  ResponseEntity<ApiError> invalidOperation(RuntimeException ex) {
    String code =
        ex instanceof InvalidTransferException
            ? "INVALID_TRANSFER"
            : ex instanceof InvalidAccountOperationException
                ? "INVALID_ACCOUNT_OPERATION"
                : "INVALID_JOURNAL";
    return response(HttpStatus.UNPROCESSABLE_ENTITY, code, ex.getMessage(), List.of());
  }

  @ExceptionHandler(BindException.class)
  ResponseEntity<ApiError> validation(BindException ex) {
    List<String> details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .distinct()
            .toList();
    return response(
        HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiError> constraintViolation(ConstraintViolationException ex) {
    List<String> details =
        ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .sorted()
            .toList();
    return response(
        HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details);
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ApiError> missingHeader(MissingRequestHeaderException ex) {
    return response(
        HttpStatus.BAD_REQUEST,
        "MISSING_REQUIRED_HEADER",
        "Required request header is missing: " + ex.getHeaderName(),
        List.of());
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException ex) {
    return response(
        HttpStatus.BAD_REQUEST,
        "MISSING_REQUIRED_PARAMETER",
        "Required request parameter is missing: " + ex.getParameterName(),
        List.of());
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ApiError> malformedRequest(Exception ex) {
    return response(
        HttpStatus.BAD_REQUEST,
        "MALFORMED_REQUEST",
        "Request body or parameter is malformed",
        List.of());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
    return response(
        HttpStatus.METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "The HTTP method is not supported for this endpoint",
        List.of());
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiError> unsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
    return response(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "The request content type is not supported",
        List.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiError> accessDenied(AccessDeniedException ex) {
    return response(
        HttpStatus.FORBIDDEN,
        "FORBIDDEN",
        "You do not have permission to access this resource",
        List.of());
  }

  @ExceptionHandler({
    TransientDataAccessException.class,
    DataAccessResourceFailureException.class,
    DependencyUnavailableException.class
  })
  ResponseEntity<ApiError> dependencyUnavailable(RuntimeException ex) {
    log.error("A required external dependency is unavailable", ex);
    return response(
        HttpStatus.SERVICE_UNAVAILABLE,
        "DEPENDENCY_UNAVAILABLE",
        "A required service is temporarily unavailable",
        List.of());
  }

  @ExceptionHandler(ReconciliationExecutionException.class)
  ResponseEntity<ApiError> reconciliationFailure(ReconciliationExecutionException ex) {
    log.error("Reconciliation execution failed runId={}", ex.getRunId(), ex);
    if (ex.getCause() instanceof TransientDataAccessException
        || ex.getCause() instanceof DataAccessResourceFailureException) {
      return response(
          HttpStatus.SERVICE_UNAVAILABLE,
          "DEPENDENCY_UNAVAILABLE",
          "A required service is temporarily unavailable",
          List.of());
    }
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "Reconciliation could not complete",
        List.of());
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ApiError> persistenceFailure(DataAccessException ex) {
    log.error("Persistence operation failed", ex);
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "A persistence operation could not complete",
        List.of());
  }

  @ExceptionHandler(InvalidOutboxStateException.class)
  ResponseEntity<ApiError> invalidOperationState(InvalidOutboxStateException ex) {
    return response(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage(), List.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception ex) {
    log.error("Unhandled request failure", ex);
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "An unexpected error occurred",
        List.of());
  }

  private ResponseEntity<ApiError> response(
      HttpStatusCode status, String code, String message, List<String> details) {
    return ResponseEntity.status(status).body(new ApiError(code, message, Instant.now(), details));
  }
}
