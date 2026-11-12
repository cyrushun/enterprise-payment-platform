package com.enterprise.payment.customer.application;

import com.enterprise.payment.audit.application.AuditService;
import com.enterprise.payment.customer.api.CreateCustomerRequest;
import com.enterprise.payment.customer.api.CustomerResponse;
import com.enterprise.payment.customer.infrastructure.CustomerEntity;
import com.enterprise.payment.customer.infrastructure.CustomerRepository;
import com.enterprise.payment.shared.error.ResourceNotFoundException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
  private final CustomerRepository repository;
  private final AuditService audit;

  public CustomerService(CustomerRepository repository, AuditService audit) {
    this.repository = repository;
    this.audit = audit;
  }

  @Transactional
  public CustomerResponse create(CreateCustomerRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    if (repository.existsByEmail(email)) throw new DuplicateCustomerEmailException(email);
    var saved =
        repository.save(
            new CustomerEntity(
                request.firstName().trim(),
                request.lastName().trim(),
                email,
                normalize(request.phoneNumber())));
    UUID actor = null;
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwt) {
      try {
        actor = UUID.fromString(jwt.getToken().getSubject());
      } catch (IllegalArgumentException ignored) {
      }
    }
    audit.record(actor, "CUSTOMER_CREATED", "CUSTOMER", saved.getId(), null, null);
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public CustomerResponse get(UUID id) {
    return repository
        .findById(id)
        .map(this::toResponse)
        .orElseThrow(
            () -> new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Customer not found"));
  }

  private CustomerResponse toResponse(CustomerEntity c) {
    return new CustomerResponse(
        c.getId(),
        c.getFirstName(),
        c.getLastName(),
        c.getEmail(),
        c.getPhoneNumber(),
        c.getStatus(),
        c.getCreatedAt());
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
