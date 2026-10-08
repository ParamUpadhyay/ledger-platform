# Ledger

A payments and ledger platform built to practice owning a system end to end: design, tests,
CI/CD, deployment and observability. See [docs/system-design.md](docs/system-design.md).

## Run it locally

Requirements: JDK 21 and Docker Desktop.

```bash
docker compose up -d postgres                 # start the database
./gradlew :services:account-service:bootRun    # port 8081
./gradlew :services:transfer-service:bootRun   # port 8082, in a second terminal
```

If you started Postgres before transfer-service existed, recreate it once so the
`ledger_transfers` database gets created: `docker compose down -v && docker compose up -d postgres`.

On Windows use `gradlew.bat` instead of `./gradlew`.

Try it:

```bash
curl -i -X POST localhost:8081/accounts \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"Ada Lovelace","currency":"USD"}'

curl localhost:8081/accounts/<id from the response>
curl localhost:8081/actuator/health

# Put money in, then move it. Retrying with the same Idempotency-Key is safe.
curl -i -X POST localhost:8082/deposits \
  -H "Content-Type: application/json" -H "Idempotency-Key: dep-1" \
  -d '{"accountId":"<alice id>","amount":"100.00","currency":"USD"}'

curl -i -X POST localhost:8082/transfers \
  -H "Content-Type: application/json" -H "Idempotency-Key: tx-1" \
  -d '{"sourceAccountId":"<alice id>","destinationAccountId":"<bob id>","amount":"25.00","currency":"USD"}'

curl localhost:8082/accounts/<alice id>/balance
```

## Test it

```bash
./gradlew build
```

This runs unit tests and integration tests. Integration tests start a real PostgreSQL in Docker
with Testcontainers and apply the real Flyway migrations, so Docker must be running.

## How we work

1. Open an issue for every change.
2. Branch from `main`: `feat/<issue>-short-name` or `fix/<issue>-short-name`.
3. Commit with [Conventional Commits](https://www.conventionalcommits.org): `feat(account): add close endpoint`.
4. Open a PR using the template. CI must be green and a review approved before merge.
5. Squash-merge, delete the branch.

Architectural choices are recorded in [docs/adr](docs/adr).

## Layout

```
services/account-service   Accounts: owner and currency
services/transfer-service  Deposits, transfers, double-entry ledger, balances
docs/                      System design and ADRs
.github/                   CI workflow, PR template, CODEOWNERS
docker-compose.yml         Local Postgres, Kafka and Keycloak
```
