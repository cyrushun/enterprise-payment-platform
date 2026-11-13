package com.enterprise.payment.notification.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.enterprise.payment.notification.application.NotificationService;
import com.enterprise.payment.security.OwnershipService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class NotificationControllerOwnershipTest {
  @Test
  void customerMustOwnTheNotificationAccount() {
    NotificationService service = mock(NotificationService.class);
    OwnershipService ownership = mock(OwnershipService.class);
    NotificationController controller = new NotificationController(service, ownership);
    UUID customerId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    Jwt jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .claim("customer_id", customerId.toString())
            .build();
    JwtAuthenticationToken auth =
        new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    when(service.list(eq(accountId), any()))
        .thenReturn(
            new PageImpl<>(
                List.<com.enterprise.payment.notification.infrastructure.NotificationEntity>of(),
                PageRequest.of(0, 20),
                0));

    controller.list(accountId, PageRequest.of(0, 20), auth);

    verify(ownership).requireAccountOwner(accountId, customerId);
    verify(service).list(eq(accountId), any());
  }

  @Test
  void ownershipFailurePreventsNotificationLookup() {
    NotificationService service = mock(NotificationService.class);
    OwnershipService ownership = mock(OwnershipService.class);
    NotificationController controller = new NotificationController(service, ownership);
    UUID customerId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    Jwt jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .claim("customer_id", customerId.toString())
            .build();
    JwtAuthenticationToken auth =
        new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    doThrow(new AccessDeniedException("not owner"))
        .when(ownership)
        .requireAccountOwner(accountId, customerId);

    assertThatThrownBy(() -> controller.list(accountId, PageRequest.of(0, 20), auth))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(service);
  }

  @Test
  void customerWithoutCustomerClaimIsDenied() {
    NotificationController controller =
        new NotificationController(mock(NotificationService.class), mock(OwnershipService.class));
    Jwt jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .build();
    JwtAuthenticationToken auth =
        new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    assertThatThrownBy(() -> controller.list(UUID.randomUUID(), PageRequest.of(0, 20), auth))
        .isInstanceOf(AccessDeniedException.class);
  }
}
