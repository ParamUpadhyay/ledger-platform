package dev.ledger.transfer.service;

/** A request that is well-formed but cannot be carried out. Maps to 422. */
public class TransferRejectedException extends RuntimeException {

    public enum Reason {
        INSUFFICIENT_FUNDS("Insufficient funds"),
        ACCOUNT_NOT_FOUND("Account not found"),
        CURRENCY_MISMATCH("Currency mismatch"),
        IDEMPOTENCY_KEY_REUSED("Idempotency key reused with a different request");

        private final String title;

        Reason(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    private final Reason reason;

    public TransferRejectedException(Reason reason, String detail) {
        super(detail);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
