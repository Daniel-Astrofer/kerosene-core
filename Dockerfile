# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM gradle:8-jdk21-alpine AS builder

WORKDIR /workspace

# Cache dependencies first
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
RUN gradle dependencies --no-daemon --quiet || true

# Copy sources and build
COPY src src
RUN gradle bootJar --no-daemon -x test

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S kerosene && adduser -S kerosene -G kerosene

WORKDIR /app

COPY --from=builder /workspace/build/libs/*.jar app.jar

RUN chown -R kerosene:kerosene /app

USER kerosene

# Do NOT expose 8080 to the host — Tor handles ingress.
# The port is only reachable inside the Docker network (app:8080).
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
