package dev.ledger.transfer.domain;

public enum TransferType {
    /** Money entering the platform from outside, debited from {@link Accounts#EXTERNAL_CASH}. */
    DEPOSIT,
    /** Money moving between two customer accounts. */
    TRANSFER
}
