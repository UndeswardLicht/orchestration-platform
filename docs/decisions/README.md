# Architecture decision records

One file per decision, written while the details are still fresh (roadmap §7, Definition of Done).
They feed the "design decisions" and "alternatives considered" parts of the thesis.

- File name: `NNNN-short-title.md`, numbered in order. Numbers are never reused.
- A decision is never edited after it is accepted. To change it, write a new ADR and set the old one's
  status to `Superseded by ADR-NNNN`.
- Keep each ADR short: 3–5 bullets per section are enough.

## Index

| ADR | Title | Status | Date |
|-----|-------|--------|------|
| [0001](0001-base-package-dev-ltp.md) | Base package and groupId `dev.ltp` | Accepted | 2026-11-21 |
| [0002](0002-one-dockerfile-for-java-modules.md) | One Dockerfile for all Java modules | Accepted | 2026-11-21 |
| [0003](0003-first-migration-in-t03.md) | First Flyway migration is written in T-03, not in the skeleton | Accepted | 2026-11-21 |
| [0004](0004-integration-tests-require-docker.md) | Integration tests require Docker (no silent skips) | Accepted | 2026-11-22 |
| [0005](0005-typescript-5-9.md) | TypeScript 5.9 instead of 7 | Accepted | 2026-11-22 |
| [0006](0006-ci-runs-mvnw-through-sh.md) | CI runs the Maven wrapper through `sh` | Accepted | 2026-11-21 |
| [0007](0007-k6-runner-and-per-second-digests.md) | k6 runner and per-second digests (SPIKE-1 results) | Accepted | 2026-11-22 |

## Template

```markdown
# ADR-NNNN: <decision in a few words>

- Status: Proposed | Accepted | Superseded by ADR-NNNN
- Date: YYYY-MM-DD
- Related: US-xx, NFR-xx, T-xx

## Context
What problem or force made a decision necessary.

## Decision
What we do.

## Alternatives considered
- Option: why not.

## Consequences
What gets easier, what gets harder, what to watch.
```
