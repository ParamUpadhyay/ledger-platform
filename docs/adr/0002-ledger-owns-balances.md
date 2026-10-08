# ADR 0002: The ledger owns balances

- Status: Accepted
- Date: 2026-10-08

## Context

account-service was created with a `balance` column. Moving money means changing two
balances and recording why, atomically. If account-service held balances, transfer-service
would need a distributed transaction (or a saga) across two databases for every transfer.

## Decision

transfer-service owns all money movement and all balances:

- Every movement is a `transfers` row plus two `ledger_entries` (one DEBIT, one CREDIT of
  equal amount). Entries are append-only; mistakes are corrected with new transfers.
- A `balances` table holds the running balance per account, updated in the same database
  transaction as the entries. It can always be rebuilt from `ledger_entries`.
- Overdrafts are prevented with `SELECT ... FOR UPDATE` on the balance rows, taken in
  ascending account id order so two opposite transfers cannot deadlock.
- Money entering the platform debits a fixed external cash account
  (`00000000-0000-0000-0000-000000000001`), which may go negative and has no balance row.
- account-service keeps account identity, owner and currency. transfer-service checks those
  over HTTP before opening its database transaction, so no locks are held during network calls.

## Consequences

- One local ACID transaction per transfer; no distributed transaction.
- account-service's `balance` column is now stale and will be removed in a follow-up.
- Reading a balance is a call to transfer-service (`GET /accounts/{id}/balance`).
- All transfers touching one account serialize on that account's row lock. That is the
  correct trade-off for money; very hot accounts would need sharded sub-balances later.

## Alternatives considered

- **Balances in account-service, saga between services**: more moving parts and
  compensating actions for the most important invariant in the system.
- **Optimistic locking (`@Version`) with retries**: works, but under contention most
  requests would fail and retry. Pessimistic locks queue them instead.
- **Computing balances by summing entries on every read**: always correct, but slows down
  as history grows and still needs a lock to prevent overdrafts.
