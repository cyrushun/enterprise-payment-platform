package com.enterprise.payment.account.api;

import com.enterprise.payment.account.application.AccountService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + response.accountId())).body(response);
    }
    @GetMapping("/{accountId}") public AccountResponse get(@PathVariable UUID accountId) { return service.get(accountId); }
    @GetMapping("/{accountId}/balance") public AccountBalanceResponse balance(@PathVariable UUID accountId) { return service.getBalance(accountId); }
}
