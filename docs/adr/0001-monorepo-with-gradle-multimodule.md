# ADR 0001: One repository, Gradle multi-module

- Status: Accepted
- Date: 2026-10-07

## Context

Ledger will have four or more services (account, transfer, fraud, notification) plus shared
infrastructure code. They change together often while the system is young, and one person
maintains them.

## Decision

Keep all services in one Git repository. Each service is a Gradle subproject under `services/`.
Shared conventions live in the root build. CI builds and tests every module on each PR.

## Consequences

- One PR can change an API and its consumer together, and CI checks both.
- Versions of Spring Boot and libraries stay aligned across services.
- CI time grows with the number of services. When it hurts, build only changed modules.
- Services must still not share database tables or call each other's internals. Communication
  goes through HTTP APIs and Kafka events only.

## Alternatives considered

- **Repo per service**: realistic for large orgs, but slows down cross-service changes and adds
  versioning overhead with no team boundaries to justify it.
- **Maven multi-module**: equally valid. Gradle chosen for build caching and the Kotlin DSL.
