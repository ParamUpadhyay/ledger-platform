CREATE TABLE accounts (
    id          UUID PRIMARY KEY,
    owner_name  VARCHAR(200)   NOT NULL,
    currency    VARCHAR(3)     NOT NULL,
    balance     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL,
    version     BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT accounts_balance_non_negative CHECK (balance >= 0)
);
