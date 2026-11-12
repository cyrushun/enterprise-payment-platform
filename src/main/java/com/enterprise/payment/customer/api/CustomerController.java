package com.enterprise.payment.customer.api;

import com.enterprise.payment.customer.application.CustomerService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
  private final CustomerService service;

  public CustomerController(CustomerService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<CustomerResponse> create(
      @Valid @RequestBody CreateCustomerRequest request) {
    CustomerResponse response = service.create(request);
    return ResponseEntity.created(URI.create("/api/v1/customers/" + response.customerId()))
        .body(response);
  }

  @GetMapping("/{customerId}")
  public CustomerResponse get(@PathVariable UUID customerId) {
    return service.get(customerId);
  }
}
