package dev.ledger.account.service;

import dev.ledger.account.domain.Account;
import dev.ledger.account.domain.AccountRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public Account open(String ownerName, String currency) {
        return accounts.save(new Account(ownerName.strip(), currency.toUpperCase(Locale.ROOT)));
    }

    @Transactional(readOnly = true)
    public Account get(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
