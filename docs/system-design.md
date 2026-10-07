# Ledger system design

Ledger moves money between accounts and keeps a double-entry record of every movement.

## Services

| Service | Owns | Talks to |
| --- | --- | --- |
| account-service | Accounts and balances (Postgres) | Publishes `account.opened` |
| transfer-service | Transfers and ledger entries (Postgres) | Calls account-service, publishes `transfer.created` via an outbox |
| fraud-service | Fraud rules and decisions | Consumes `transfer.created`, publishes `transfer.flagged` |
| notification-service | Delivery attempts | Consumes transfer events, retries with a dead-letter topic |

## Rules that hold everywhere

- Each service owns its database. No service reads another's tables.
- Money is `NUMERIC(19,4)` in the database and `BigDecimal` in Java, never `double`.
- Every write API accepts an `Idempotency-Key` header so clients can retry safely.
- Events are written to an outbox table in the same transaction as the state change, then
  relayed to Kafka. This avoids "saved to DB but event lost" bugs.
- Every request carries a trace ID (OpenTelemetry) through HTTP and Kafka.

## Status

- [x] account-service: open and fetch accounts
- [ ] transfer-service
- [ ] fraud-service
- [ ] notification-service
- [ ] API gateway and Keycloak auth
