# ── Stage 1: Build ──────────────────────────────────────────────────────────
# CRITICAL: Use a Debian/glibc builder to match the Distroless Debian runtime.
# Alpine (musl libc) binaries are ABI-incompatible with glibc — this causes
# silent crashes or memory corruption when native libraries (IPC_LOCK, TPM JNI,
# mlock syscalls) are invoked in the runtime stage.
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

# Copy the Gradle wrapper first so it can download Gradle itself
COPY gradlew gradlew.bat ./
COPY gradle gradle
RUN chmod +x gradlew

# Cache dependencies before copying sources (layer-cache optimization)
COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon --quiet || true

# Copy sources and build
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM gcr.io/distroless/java21-debian12 AS runtime

WORKDIR /app

# The distroless image doesn't have chown/adduser. We copy with explicit ownership.
COPY --chown=1000:1000 --from=builder /workspace/build/libs/*.jar /app/app.jar

# Run as UID 1000 to match the Tor UDS socket permissions (Phase 3 hardening)
USER 1000:1000

# Do NOT expose 8080 to the host — Tor handles ingress.
# The port is only reachable inside the Docker network (app:8080).
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
