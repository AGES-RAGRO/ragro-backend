# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml and cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as a non-root user (defense in depth: a compromised process can't write outside /app).
RUN addgroup -S app && adduser -S app -G app

# Copy JAR from builder
COPY --from=builder /app/target/*.jar app.jar

USER app

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Cap the heap against the container's cgroup memory limit so the JVM isn't OOM-killed by ECS
# (JDK default is 25%; 75% leaves headroom for metaspace/non-heap/Ollama client buffers).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
