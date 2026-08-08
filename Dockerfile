# ============================================================
# Stage 1: Build — Maven + Java 21
# ============================================================
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

# Cache Maven dependencies first (invalidated only if pom.xml changes).
# The repository uses Maven but does not include a Maven wrapper.
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# Copy source and build (skip tests — tests run in CI or explicitly)
COPY src src
RUN mvn package -DskipTests -B -q

# ============================================================
# Stage 2: Runtime — slim JRE 21
# ============================================================
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# Non-root user for security
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

# Copy fat jar from builder stage
COPY --from=builder /workspace/target/ParkingSystem-wego-1.0.0.jar app.jar

# Ensure non-root user owns the app directory
RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

# JVM tuning for containerized environments (Java 21)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
