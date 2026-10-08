package dev.ledger.transfer.service;

import static dev.ledger.transfer.service.TransferRejectedException.Reason.INSUFFICIENT_FUNDS;

import dev.ledger.transfer.domain.Accounts;
import dev.ledger.transfer.domain.Balance;
import dev.ledger.transfer.domain.BalanceRepository;
import dev.ledger.transfer.domain.LedgerEntry;
import dev.ledger.transfer.domain.LedgerEntryRepository;
import dev.ledger.transfer.domain.OutboxEvent;
import dev.ledger.transfer.domain.OutboxRepository;
import dev.ledger.transfer.domain.Transfer;
import dev.ledger.transfer.domain.TransferRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes one transfer atomically: the transfer row, its two ledger entries, the balance
 * updates and the outbox event all commit together or not at all.
 */
@Service
public class LedgerWriter {

    private final TransferRepository transfers;
    private final LedgerEntryRepository entries;
    private final BalanceRepository balances;
    private final OutboxRepository outbox;
    private final JsonMapper json;

    public LedgerWriter(TransferRepository transfers, LedgerEntryRepository entries, BalanceRepository balances,
            OutboxRepository outbox, JsonMapper json) {
        this.transfers = transfers;
        this.entries = entries;
        this.balances = balances;
        this.outbox = outbox;
        this.json = json;
    }

    @Transactional
    public Transfer write(TransferCommand command) {
        Map<UUID, Balance> locked = lockBalances(command);

        // A retry with the same key may have committed while we waited for the locks.
        // Bail out so the caller replays it, instead of judging funds a second time.
        if (transfers.existsByIdempotencyKey(command.idempotencyKey())) {
            throw new DataIntegrityViolationException("Duplicate idempotency key " + command.idempotencyKey());
        }

        Balance source = locked.get(command.sourceAccountId());
        if (source != null && !source.covers(command.amount())) {
            throw new TransferRejectedException(INSUFFICIENT_FUNDS,
                    "Account " + command.sourceAccountId() + " cannot cover " + command.amount() + " " + command.currency());
        }

        Transfer transfer = transfers.save(new Transfer(command.idempotencyKey(), command.fingerprint(), command.type(),
                command.sourceAccountId(), command.destinationAccountId(), command.amount(), command.currency()));

        entries.save(LedgerEntry.debit(transfer));
        entries.save(LedgerEntry.credit(transfer));
        if (source != null) {
            source.debit(command.amount());
        }
        locked.get(command.destinationAccountId()).credit(command.amount());

        outbox.save(new OutboxEvent("transfer", transfer.getId(), "transfer.completed", toJson(transfer)));
        return transfer;
    }

    /**
     * Locks every customer account in the transfer, always in ascending id order. Two transfers
     * A→B and B→A therefore wait on each other instead of deadlocking.
     */
    private Map<UUID, Balance> lockBalances(TransferCommand command) {
        Map<UUID, Balance> locked = new LinkedHashMap<>();
        Stream.of(command.sourceAccountId(), command.destinationAccountId())
                .filter(id -> !Objects.equals(id, Accounts.EXTERNAL_CASH))
                .sorted()
                .forEach(id -> {
                    balances.createIfMissing(id, command.currency());
                    locked.put(id, balances.lockById(id).orElseThrow());
                });
        return locked;
    }

    private String toJson(Transfer transfer) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transferId", transfer.getId());
        payload.put("type", transfer.getType());
        payload.put("sourceAccountId", transfer.getSourceAccountId());
        payload.put("destinationAccountId", transfer.getDestinationAccountId());
        payload.put("amount", transfer.getAmount().toPlainString());
        payload.put("currency", transfer.getCurrency());
        payload.put("createdAt", transfer.getCreatedAt().toString());
        return json.writeValueAsString(payload);
    }
}
