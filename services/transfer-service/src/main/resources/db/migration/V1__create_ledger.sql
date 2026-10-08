-- One row per money movement. The idempotency key makes client retries safe.
CREATE TABLE transfers (
    id                     UUID PRIMARY KEY,
    idempotency_key        VARCHAR(100)   NOT NULL UNIQUE,
    request_fingerprint    VARCHAR(64)    NOT NULL,
    type                   VARCHAR(20)    NOT NULL,
    source_account_id      UUID           NOT NULL,
    destination_account_id UUID           NOT NULL,
    amount                 NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency               VARCHAR(3)     NOT NULL,
    created_at             TIMESTAMPTZ    NOT NULL
);

-- Double-entry ledger: every transfer writes one DEBIT and one CREDIT of equal amount.
-- Rows are append-only; corrections are new transfers, never updates.
CREATE TABLE ledger_entries (
    id          BIGSERIAL PRIMARY KEY,
    transfer_id UUID           NOT NULL REFERENCES transfers (id),
    account_id  UUID           NOT NULL,
    direction   VARCHAR(6)     NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount      NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency    VARCHAR(3)     NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL
);

CREATE INDEX ledger_entries_account_id_idx ON ledger_entries (account_id);

-- Running balance per customer account, kept in step with ledger_entries in the same
-- transaction. Rows are locked (SELECT ... FOR UPDATE) before a debit to prevent overdrafts.
CREATE TABLE balances (
    account_id UUID PRIMARY KEY,
    currency   VARCHAR(3)     NOT NULL,
    amount     NUMERIC(19, 4) NOT NULL CHECK (amount >= 0),
    updated_at TIMESTAMPTZ    NOT NULL
);

-- Transactional outbox: events are stored with the state change, then relayed to Kafka.
CREATE TABLE outbox (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ
);

CREATE INDEX outbox_unpublished_idx ON outbox (created_at) WHERE published_at IS NULL;
