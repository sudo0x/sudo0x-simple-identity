# Multi-stage build for production
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Download dependencies first (improves layer caching)
RUN ./mvnw dependency:go-offline -q

COPY src/ src/
RUN ./mvnw package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy the built jar
COPY --from=builder /app/target/*.jar app.jar

# Optional: mount directory for RSA keys in production
RUN mkdir -p /app/keys && chown -R appuser:appgroup /app

USER appuser

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
