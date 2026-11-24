# ADR-0005: TypeScript 5.9 instead of 7

- Status: Accepted
- Date: 2026-11-22
- Related: US-17, US-25, T-01

## Context
The skeleton used TypeScript 7, the new native compiler. `openapi-typescript`, which generates the UI's
API types from the springdoc spec, declares the peer dependency `typescript ^5.x`. To avoid an npm peer
conflict it ran through `npx --yes openapi-typescript@7.13.0`, so it was outside `package-lock.json` and
needed network access. Much of the frontend tooling still targets TypeScript 5.

## Decision
Use `typescript 5.9.3` and `openapi-typescript 7.13.0` as exact-pinned dev dependencies.
`npm run api:gen` calls the local binary. TypeScript 6+ enables `strict` by default and 5.9 does not, so
`"strict": true` is set explicitly in `tsconfig.app.json` and `tsconfig.node.json`.

## Alternatives considered
- Keep TypeScript 7 with an npm `overrides` entry for the peer dependency: works, but hides the
  incompatibility.
- Keep TypeScript 7 and `npx`: the generator is not locked.
- Another generator (orval, openapi-generator): more output and more to learn, with no benefit at this
  stage.

## Consequences
- The whole toolchain is locked in `package-lock.json`, so builds are reproducible offline.
- The compiler is slower than TS 7, which doesn't matter at this project's size.
- Verified 2026-11-22: lint, typecheck, Vitest (3), build and Playwright (18) are green. `api:gen` ran
  against a live Control Service.
- Upgrade to TS 7 once `openapi-typescript` supports it.
