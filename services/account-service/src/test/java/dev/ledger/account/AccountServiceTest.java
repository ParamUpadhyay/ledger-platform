package dev.ledger.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.ledger.account.domain.Account;
import dev.ledger.account.domain.AccountRepository;
import dev.ledger.account.service.AccountNotFoundException;
import dev.ledger.account.service.AccountService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    AccountRepository accounts;

    @InjectMocks
    AccountService accountService;

    @Test
    void opensAccountWithZeroBalanceAndNormalizedCurrency() {
        when(accounts.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account account = accountService.open("  Ada Lovelace ", "usd");

        assertThat(account.getOwnerName()).isEqualTo("Ada Lovelace");
        assertThat(account.getCurrency()).isEqualTo("USD");
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void throwsWhenAccountIsMissing() {
        UUID id = UUID.randomUUID();
        when(accounts.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.get(id))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}
