package dev.ledger.transfer.service;

import static dev.ledger.transfer.service.TransferRejectedException.Reason.ACCOUNT_NOT_FOUND;
import static dev.ledger.transfer.service.TransferRejectedException.Reason.CURRENCY_MISMATCH;
import static dev.ledger.transfer.service.TransferRejectedException.Reason.IDEMPOTENCY_KEY_REUSED;

import dev.ledger.transfer.client.AccountClient;
import dev.ledger.transfer.domain.Accounts;
import dev.ledger.transfer.domain.Balance;
import dev.ledger.transfer.domain.BalanceRepository;
import dev.ledger.transfer.domain.Transfer;
import dev.ledger.transfer.domain.TransferRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class TransferService {

    public record Result(Transfer transfer, boolean replayed) {
    }

    private final TransferRepository transfers;
    private final BalanceRepository balances;
    private final LedgerWriter ledger;
    private final AccountClient accounts;

    public TransferService(TransferRepository transfers, BalanceRepository balances, LedgerWriter ledger,
            AccountClient accounts) {
        this.transfers = transfers;
        this.balances = balances;
        this.ledger = ledger;
        this.accounts = accounts;
    }

    /**
     * Executes the command once per idempotency key. A retry returns the original transfer.
     * Account checks call account-service before the database transaction starts, so no row
     * locks are held while waiting on the network.
     */
    public Result submit(TransferCommand command) {
        Optional<Result> previous = replay(command);
        if (previous.isPresent()) {
            return previous.get();
        }

        verifyAccount(command.sourceAccountId(), command.currency());
        verifyAccount(command.destinationAccountId(), command.currency());

        try {
            return new Result(ledger.write(command), false);
        } catch (DataIntegrityViolationException e) {
            // Another request with the same key committed between our check and our insert.
            return replay(command).orElseThrow(() -> e);
        }
    }

    public Optional<Transfer> find(UUID id) {
        return transfers.findById(id);
    }

    public Optional<Balance> balance(UUID accountId) {
        return balances.findById(accountId);
    }

    private Optional<Result> replay(TransferCommand command) {
        return transfers.findByIdempotencyKey(command.idempotencyKey()).map(existing -> {
            if (!existing.getRequestFingerprint().equals(command.fingerprint())) {
                throw new TransferRejectedException(IDEMPOTENCY_KEY_REUSED,
                        "Idempotency key '" + command.idempotencyKey() + "' was already used for a different request");
            }
            return new Result(existing, true);
        });
    }

    private void verifyAccount(UUID accountId, String currency) {
        if (Accounts.EXTERNAL_CASH.equals(accountId)) {
            return;
        }
        AccountClient.AccountView account = accounts.find(accountId)
                .orElseThrow(() -> new TransferRejectedException(ACCOUNT_NOT_FOUND, "Account " + accountId + " does not exist"));
        if (!account.currency().equalsIgnoreCase(currency)) {
            throw new TransferRejectedException(CURRENCY_MISMATCH,
                    "Account " + accountId + " holds " + account.currency() + ", not " + currency);
        }
    }
}
