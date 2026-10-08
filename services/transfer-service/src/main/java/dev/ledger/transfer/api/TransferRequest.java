package dev.ledger.transfer.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record TransferRequest(
        @NotNull UUID sourceAccountId,
        @NotNull UUID destinationAccountId,
        @NotNull @DecimalMin(value = "0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotNull @Pattern(regexp = "[A-Za-z]{3}", message = "must be an ISO 4217 code like USD") String currency) {

    @AssertTrue(message = "source and destination must be different accounts")
    boolean isDifferentAccounts() {
        return sourceAccountId == null || !Objects.equals(sourceAccountId, destinationAccountId);
    }
}
