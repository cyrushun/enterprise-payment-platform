package com.enterprise.payment.account.api;

import static org.mockito.Mockito.*;

import com.enterprise.payment.account.application.AccountService;
import com.enterprise.payment.security.OwnershipService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AccountControllerListTest {
  @Test
  void customerQueryCannotOverrideTheSignedCustomerClaim() {
    AccountService service = mock(AccountService.class);
    AccountController controller = new AccountController(service, mock(OwnershipService.class));
    UUID claimCustomer = UUID.randomUUID();
    UUID requestedCustomer = UUID.randomUUID();
    Jwt jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .claim("customer_id", claimCustomer.toString())
            .build();
    JwtAuthenticationToken authentication =
        new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

    controller.list(requestedCustomer, authentication);

    verify(service).listForCustomer(claimCustomer);
    verifyNoMoreInteractions(service);
  }
}
