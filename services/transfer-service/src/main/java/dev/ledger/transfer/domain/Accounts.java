package dev.ledger.transfer.domain;

import java.util.UUID;

public final class Accounts {

    /**
     * The contra account for money entering the platform. Deposits debit it, so its ledger
     * balance is negative by design and it has no row in {@code balances}.
     */
    public static final UUID EXTERNAL_CASH = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private Accounts() {
    }
}
