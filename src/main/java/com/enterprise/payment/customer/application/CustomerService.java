package com.enterprise.payment.customer.application;

import com.enterprise.payment.customer.api.CreateCustomerRequest;
import com.enterprise.payment.customer.api.CustomerResponse;
import com.enterprise.payment.customer.infrastructure.CustomerEntity;
import com.enterprise.payment.customer.infrastructure.CustomerRepository;
import com.enterprise.payment.shared.error.ResourceNotFoundException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
  private final CustomerRepository repository;

  public CustomerService(CustomerRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public CustomerResponse create(CreateCustomerRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    if (repository.existsByEmail(email)) throw new DuplicateCustomerEmailException(email);
    return toResponse(
        repository.save(
            new CustomerEntity(
                request.firstName().trim(),
                request.lastName().trim(),
                email,
                normalize(request.phoneNumber()))));
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
