package dev.ledger.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ledger.transfer.domain.TransferType;
import dev.ledger.transfer.service.TransferCommand;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferCommandTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void equivalentRequestsHaveTheSameFingerprint() {
        TransferCommand first = new TransferCommand("k", TransferType.TRANSFER, A, B, new BigDecimal("10.5"), "usd");
        TransferCommand retry = new TransferCommand("k", TransferType.TRANSFER, A, B, new BigDecimal("10.5000"), "USD");

        assertThat(retry.fingerprint()).isEqualTo(first.fingerprint());
    }

    @Test
    void differentAmountsHaveDifferentFingerprints() {
        TransferCommand first = new TransferCommand("k", TransferType.TRANSFER, A, B, new BigDecimal("10"), "USD");
        TransferCommand other = new TransferCommand("k", TransferType.TRANSFER, A, B, new BigDecimal("11"), "USD");

        assertThat(other.fingerprint()).isNotEqualTo(first.fingerprint());
    }

    @Test
    void rejectsMoreThanFourDecimalPlaces() {
        assertThatThrownBy(() -> new TransferCommand("k", TransferType.TRANSFER, A, B, new BigDecimal("0.00001"), "USD"))
                .isInstanceOf(ArithmeticException.class);
    }
}
