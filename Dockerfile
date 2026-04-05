# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM gradle:8.7-jdk21 AS builder
WORKDIR /build
COPY . .
RUN gradle shadowJar --no-daemon -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
LABEL org.opencontainers.image.title="AgentShell" \
      org.opencontainers.image.description="Resume-first approval-gated agent runtime" \
      org.opencontainers.image.source="https://github.com/OlehHavrilko/AgentShell"

RUN addgroup -S agentshell && adduser -S agentshell -G agentshell
WORKDIR /app

COPY --from=builder /build/build/libs/*-all.jar app.jar
RUN mkdir -p /data/agentshell && chown -R agentshell:agentshell /data/agentshell /app

USER agentshell

ENV AGENTSHELL_DB=/data/agentshell/agent.db \
    AGENTSHELL_LOG_DIR=/data/agentshell/logs

VOLUME /data/agentshell

EXPOSE 8080 9090

ENTRYPOINT ["java", \
  "-Dagentshell.logDir=/data/agentshell/logs", \
  "-jar", "/app/app.jar"]
CMD ["--agent", "default", "--demo"]
