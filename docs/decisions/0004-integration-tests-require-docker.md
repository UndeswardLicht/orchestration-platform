# ADR-0004: Integration tests require Docker (no silent skips)

- Status: Accepted
- Date: 2026-11-22
- Related: T-02, NFR-05, US-08

## Context
The Control Service integration tests use Testcontainers (PostgreSQL 18.7). The skeleton marked them
`@Testcontainers(disabledWithoutDocker = true)`, so they were skipped while the local Docker login was
broken. A skipped test looks harmless in the Maven output ("Skipped: 3") and hides real failures. JaCoCo
then reports about 0 % coverage for the module.

## Decision
Docker is a hard requirement for `./mvnw verify`. The skip flag is removed. The PostgreSQL container is a
Spring `@ServiceConnection` bean, so no `@Testcontainers` annotation is needed. Without Docker the tests
fail.

## Alternatives considered
- Keep skipping when Docker is unavailable: convenient on a laptop, but broken Docker goes unnoticed.
- Skip only outside CI (fail when `CI=true`): CI stays strict, but local runs can still be misleading.

## Consequences
- Docker is already needed to run k6 (US-08) and the Compose stack, so this adds no new requirement.
- A broken local Docker now shows as a red build with a clear error, not as "skipped".
- Verified 2026-11-22: `./mvnw verify` runs 10 tests, 0 skipped.
