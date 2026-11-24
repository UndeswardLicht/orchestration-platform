# ADR-0003: First Flyway migration is written in T-03, not in the skeleton

- Status: Accepted
- Date: 2026-11-21
- Related: T-01, T-03

## Context
Flyway stores a checksum for every applied migration and refuses to start if an applied file changes. The
project rule is that every schema change is a new migration. The data model in `05-tech-stack.md` §5 is
marked "first cut — refine in T-03".

## Decision
The skeleton ships an empty `db/migration/` folder. `V1__init.sql` is written in T-03 together with the
JPA entities and the ER diagram. `spring.jpa.hibernate.ddl-auto=validate` makes the entities and the
schema agree.

## Alternatives considered
- Commit the draft model as `V1__init.sql` now. This is safe as long as no database that matters has
  applied it (Testcontainers starts a fresh database each time). But it risks the draft being deployed by
  mistake before it is reviewed.

## Consequences
- The Control Service starts with Flyway enabled and no migrations (verified by the integration tests,
  2026-11-22).
- Until the first deployment to the Azure VM, `V1__init.sql` may still be edited. After that, never.
