# ADR-0002: One Dockerfile for all Java modules

- Status: Accepted
- Date: 2026-11-21
- Related: T-01, T-08, NFR-09

## Context
Three Java services (`control-service`, `worker-service`, `demo-target`) build the same way: Maven
produces a Spring Boot fat jar, which runs on a JRE. They need the same base image, non-root user and JVM
flags.

## Decision
One multi-stage root `Dockerfile`, with the module chosen by `--build-arg MODULE=<module>`:
- build stage: `maven:*-eclipse-temurin-25`, `mvn -pl $MODULE -am package` with a BuildKit cache for
  `~/.m2`;
- runtime stage: `eclipse-temurin:25.*-jre-noble`, non-root user `ltp`, `curl` for the Compose health
  checks, `-XX:MaxRAMPercentage=75`.

The web image (`web/Dockerfile`, Node build → Caddy) stays separate because its toolchain is different.

## Alternatives considered
- One Dockerfile per module: easier to read and each image can differ, but the steps are duplicated
  three times.
- Spring Boot Buildpacks (`spring-boot:build-image`): no Dockerfile and good defaults, but less control and
  slower builds.
- Jib: no Docker daemon needed and dependencies go into separate layers. It is the best upgrade if image
  push and pull become slow.

## Consequences
- Base image, user and JVM flags are changed in one place.
- Every code change makes a new fat-jar layer (about 60 MB). Layered jars (`-Djarmode=tools extract
  --layers`) or Jib would fix this if it matters.
- Settings needed by one module only (the `/k6-io` mount point for the worker) end up in all images,
  where they are harmless.
