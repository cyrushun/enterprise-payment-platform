package com.enterprise.payment.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.enterprise.payment.account.domain.AccountType;
import com.enterprise.payment.account.infrastructure.AccountEntity;
import com.enterprise.payment.account.infrastructure.AccountRepository;
import com.enterprise.payment.audit.application.AuditService;
import com.enterprise.payment.customer.infrastructure.CustomerRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountServiceListTest {
  @Test
  void listsOnlyAccountsReturnedForTheRequestedCustomer() {
    AccountRepository accounts = mock(AccountRepository.class);
    UUID customerId = UUID.randomUUID();
    AccountEntity account =
        new AccountEntity(customerId, "1234567890123456", AccountType.SAVINGS, "MYR");
    when(accounts.findByCustomerIdOrderByOpenedAtDesc(customerId)).thenReturn(List.of(account));
    AccountService service =
        new AccountService(accounts, mock(CustomerRepository.class), mock(AuditService.class));

    var result = service.listForCustomer(customerId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).customerId()).isEqualTo(customerId);
    assertThat(result.get(0).accountId()).isEqualTo(account.getId());
    verify(accounts).findByCustomerIdOrderByOpenedAtDesc(customerId);
    verifyNoMoreInteractions(accounts);
  }
}
