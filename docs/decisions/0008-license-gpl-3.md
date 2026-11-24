# ADR-0008: License the code under GPL-3.0-or-later

- Status: Accepted
- Date: 2026-11-24
- Related: T-12

## Context
The repository is public, but it had no licence, so by default nobody could legally reuse, modify or
redistribute the code. The author wants the thesis code to be open source, and wants any changed
version that is distributed to stay open source too.

## Decision
- The whole project is licensed under the **GNU GPL, version 3 or (at your option) any later
  version** (SPDX `GPL-3.0-or-later`). The full text is in `LICENSE`, taken unmodified from
  <https://www.gnu.org/licenses/gpl-3.0.txt>.
- The licence is also declared in `pom.xml` (`<licenses>`), `web/package.json` (`license`) and the
  OCI label `org.opencontainers.image.licenses` of both images, so tools and GHCR show it.
- No per-file headers for now. The README notice plus `LICENSE` is enough for a single-author repo.

## Alternatives considered
- **MIT or Apache-2.0:** maximise reuse, but allow closed-source forks. Not what the author wants.
- **AGPL-3.0:** would also cover people who run a changed platform as a network service. Stronger
  than needed for a thesis project, and it puts off some company users.
- **GPL-3.0-only:** prevents automatic moves to a future GPL version. "Or later" is the form the FSF
  recommends, and it keeps the code compatible with future GPL-licensed projects.

## Consequences
- Dependencies are compatible: Spring, docker-java, t-digest, Jackson and Caddy are Apache-2.0;
  React, Mantine and Vite are MIT; the PostgreSQL JDBC driver is BSD-2-Clause. All may be combined
  with GPLv3 code.
- k6 (AGPL-3.0) runs as an unmodified separate container and is not linked in, so its licence does not
  apply to this code. Do not copy k6 source into the repo.
- Anyone who distributes binaries or images of a modified version must also offer its source.
- New dependencies must be GPLv3-compatible (for example not EPL-1.0 or SSPL). Check before adding one.
- Contributions from others (if any) are accepted under the same licence.
