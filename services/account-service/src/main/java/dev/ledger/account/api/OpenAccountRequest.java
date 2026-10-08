package dev.ledger.account.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OpenAccountRequest(
        @NotBlank @Size(max = 200) String ownerName,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be an ISO 4217 code like USD") String currency) {
}
