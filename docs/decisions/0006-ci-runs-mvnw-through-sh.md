# ADR-0006: CI runs the Maven wrapper through `sh`

- Status: Accepted
- Date: 2026-11-21
- Related: T-02

## Context
The repository is developed on Windows, where files do not get the Unix executable bit in git. On a Linux
runner, `./mvnw` then fails with "Permission denied".

## Decision
CI calls `sh ./mvnw -B verify`, which does not need the executable bit.

## Alternatives considered
- Set the bit once with `git update-index --chmod=+x mvnw` and call `./mvnw`. This also fixes the Azure
  VM and other Linux users. It is a one-line change that can be done at any time.

## Consequences
- CI works whatever the file mode is.
- On Linux or macOS, people must also use `sh ./mvnw` unless the bit is set.
