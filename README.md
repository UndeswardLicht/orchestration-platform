# LTP — Distributed Load Testing Orchestration Platform

Self-hosted platform for small teams: versioned k6 scenarios, distributed runs on workers (k6 in
resource-limited Docker containers), cluster-wide aggregation with merged t-digests, live view over
SSE, run history, baseline comparison and a quality-gate REST API for CI.

## Repository layout

| Path | What |
|------|------|
| `common/` | Worker protocol DTOs, t-digest (de)serialization, execution-segment math |
| `control-service/` | REST API, SSE, scheduler, aggregator, regression detector, security, Flyway migrations |
| `worker-service/` | Registration, heartbeat, k6 container runner, JSON tailer, metrics reporter |
| `demo-target/` | System under test with latency / error / regression knobs |
| `web/` | React + TypeScript + Vite + Mantine UI |
| `experiments/` | Python experiment runner and analysis notebooks |
| `deploy/` | Docker Compose stack, Caddyfile, `.env.example` |
| `.github/workflows/` | CI, CD and a sample performance-gate pipeline |

## Build and test

Requires JDK 25, Node 24 and Docker (Testcontainers).

```sh
./mvnw verify                 # all Java modules
cd web && npm ci && npm run test
npm run e2e                   # Playwright at 360/768/1280 px + axe (needs `npx playwright install chromium`)
```

## Run locally

```sh
cp deploy/.env.example deploy/.env   # then change the secrets
docker compose -f deploy/docker-compose.yml up -d                   # caddy + control + postgres
docker compose -f deploy/docker-compose.yml --profile demo up -d    # + 2 workers + Demo Target
```

UI: https://localhost · API docs: https://localhost/swagger-ui.html

For development, run the Control Service from the IDE (PostgreSQL on `localhost:5432`) and
`cd web && npm run dev`; Vite proxies `/api` to `localhost:8080`.

## License

Copyright (C) 2026 UndeswardLicht

This program is free software: you can redistribute it and/or modify it under the terms of the GNU
General Public License as published by the Free Software Foundation, either version 3 of the License,
or (at your option) any later version. It is distributed WITHOUT ANY WARRANTY; see [`LICENSE`](LICENSE)
for the full text.

Third-party components keep their own licences. The worker runs the unmodified `grafana/k6` image
(AGPL-3.0) as a separate container; it is not linked into or distributed with this code.
