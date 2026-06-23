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

# Disable DNS negative cache (temurin defaults to 10s). Under musl/Alpine, sporadic EAI_AGAIN on
# routes/maps.googleapis.com got cached 10s, so app retries hit the same cached failure. Must be set
# in java.security: -Dsun.net.inetaddr.negative.ttl is ignored when the property is set there.
RUN sed -i 's/^networkaddress.cache.negative.ttl=.*/networkaddress.cache.negative.ttl=0/' \
    "$JAVA_HOME/conf/security/java.security" \
    && grep -q '^networkaddress.cache.negative.ttl=0' "$JAVA_HOME/conf/security/java.security"

# Copy JAR from builder
COPY --from=builder /app/target/*.jar app.jar

USER app

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Cap heap at 75% of cgroup limit (JDK default 25%) so ECS doesn't OOM-kill; leaves headroom for
# metaspace/non-heap/HTTP buffers. DNS negative cache disabled via java.security above.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
