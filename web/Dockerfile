# syntax=docker/dockerfile:1
# Caddy image that serves the SPA and proxies /api to the Control Service.
# Build from the repo root:  docker build -f web/Dockerfile -t ltp/web .

FROM node:24.22.3-alpine AS build
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY web/ ./
RUN npm run build

FROM caddy:2.11.9-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /web/dist /srv
