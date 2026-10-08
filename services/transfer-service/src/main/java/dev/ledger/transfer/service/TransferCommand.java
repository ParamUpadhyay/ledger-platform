package dev.ledger.transfer.service;

import dev.ledger.transfer.domain.TransferType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** A validated request to move money, normalized so equal requests compare equal. */
public record TransferCommand(
        String idempotencyKey,
        TransferType type,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency) {

    public TransferCommand {
        amount = amount.setScale(4, RoundingMode.UNNECESSARY);
        currency = currency.toUpperCase(Locale.ROOT);
    }

    /**
     * SHA-256 of the request body. A retry with the same idempotency key must send the same
     * body; a different fingerprint means the client reused a key by mistake.
     */
    public String fingerprint() {
        String canonical = String.join("|", type.name(), sourceAccountId.toString(),
                destinationAccountId.toString(), amount.toPlainString(), currency);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
