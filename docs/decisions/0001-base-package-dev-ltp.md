# ADR-0001: Base package and groupId `dev.ltp`

- Status: Accepted
- Date: 2026-11-21
- Related: T-01

## Context
Java convention is a reversed domain name for the Maven groupId and the base package. The project will
not be published to Maven Central, so the name only needs to be unique inside this repository.

## Decision
Use `dev.ltp` for the groupId and as the base package (`dev.ltp.common`, `dev.ltp.control`,
`dev.ltp.worker`, `dev.ltp.demo`).

## Alternatives considered
- `io.github.<github-user>.ltp`: follows the convention for GitHub projects and uses a namespace the
  author controls, but is long and ties the code to a personal account.
- The university's domain: ties the code to the institution.

## Consequences
- Short, neutral package names.
- The author does not own `ltp.dev`. This is irrelevant unless the artifacts are ever published.
- Renaming later is a repository-wide search and replace. The name is kept as it is (decided
  2026-11-22).
