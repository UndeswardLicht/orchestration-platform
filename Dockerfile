# syntax=docker/dockerfile:1
# One image recipe for every Java module. Build from the repo root:
#   docker build --build-arg MODULE=control-service -t ltp/control .
#   docker build --build-arg MODULE=worker-service  -t ltp/worker  .
#   docker build --build-arg MODULE=demo-target     -t ltp/demo-target .

FROM maven:3.9.16-eclipse-temurin-25-noble AS build
ARG MODULE
WORKDIR /src
COPY pom.xml .
COPY common/pom.xml common/
COPY control-service/pom.xml control-service/
COPY worker-service/pom.xml worker-service/
COPY demo-target/pom.xml demo-target/
COPY common/src common/src
COPY ${MODULE}/src ${MODULE}/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:25.0.5.1_2-jre-noble
ARG MODULE
LABEL org.opencontainers.image.licenses="GPL-3.0-or-later" \
      org.opencontainers.image.source="https://github.com/UndeswardLicht/orchestration-platform"
# curl is used by the Compose health checks; the Temurin JRE image does not ship it
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system ltp && useradd --system --gid ltp --no-create-home ltp \
    # Mount point of the k6-io named volume shared with k6 containers (worker only; SPIKE-1 settles the permissions)
    && mkdir -p /k6-io && chmod 1777 /k6-io
COPY --from=build /src/${MODULE}/target/${MODULE}.jar /app/app.jar
USER ltp
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
