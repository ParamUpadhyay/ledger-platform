package dev.ledger.transfer.api;

import dev.ledger.transfer.domain.Transfer;
import dev.ledger.transfer.domain.TransferType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        TransferType type,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        Instant createdAt) {

    static TransferResponse from(Transfer transfer) {
        return new TransferResponse(transfer.getId(), transfer.getType(), transfer.getSourceAccountId(),
                transfer.getDestinationAccountId(), transfer.getAmount(), transfer.getCurrency(),
                transfer.getCreatedAt());
    }
}
