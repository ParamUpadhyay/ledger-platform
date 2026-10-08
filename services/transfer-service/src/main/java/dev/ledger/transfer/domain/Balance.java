package dev.ledger.transfer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "balances")
public class Balance {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Balance() {
        // for JPA
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean covers(BigDecimal debit) {
        return amount.compareTo(debit) >= 0;
    }

    public void debit(BigDecimal value) {
        if (!covers(value)) {
            throw new IllegalStateException("Debit exceeds balance for account " + accountId);
        }
        amount = amount.subtract(value);
        updatedAt = Instant.now();
    }

    public void credit(BigDecimal value) {
        amount = amount.add(value);
        updatedAt = Instant.now();
    }
}
