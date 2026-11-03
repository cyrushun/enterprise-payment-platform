package com.enterprise.payment.shared.error;

import com.enterprise.payment.account.application.InvalidAccountOperationException;
import com.enterprise.payment.customer.application.DuplicateCustomerEmailException;
import com.enterprise.payment.payment.domain.*;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
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
    if (ex instanceof TransferConflictException)
      return response(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", ex.getMessage(), List.of());
    return response(
        HttpStatus.CONFLICT,
        "RESOURCE_CONFLICT",
        "A resource with the supplied unique value already exists",
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

  @ExceptionHandler({InvalidTransferException.class, InvalidAccountOperationException.class})
  ResponseEntity<ApiError> invalidOperation(RuntimeException ex) {
    String code =
        ex instanceof InvalidTransferException ? "INVALID_TRANSFER" : "INVALID_ACCOUNT_OPERATION";
    return response(HttpStatus.UNPROCESSABLE_ENTITY, code, ex.getMessage(), List.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
    List<String> details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList();
    return response(
        HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details);
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ApiError> missingHeader(MissingRequestHeaderException ex) {
    return response(
        HttpStatus.BAD_REQUEST,
        "MISSING_REQUIRED_HEADER",
        "Idempotency-Key header is required",
        List.of());
  }

  private ResponseEntity<ApiError> response(
      HttpStatus status, String code, String message, List<String> details) {
    return ResponseEntity.status(status).body(new ApiError(code, message, Instant.now(), details));
  }
}
