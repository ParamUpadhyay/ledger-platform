package dev.ledger.transfer.api;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceResponse(UUID accountId, BigDecimal amount, String currency) {
}
