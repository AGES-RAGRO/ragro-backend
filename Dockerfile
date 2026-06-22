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

# DNS negative cache: o java.security do temurin define networkaddress.cache.negative.ttl=10 —
# o JVM cacheia lookups que FALHARAM por 10s. No resolver musl/Alpine, falhas ESPORÁDICAS de
# resolução (EAI_AGAIN de routes/maps.googleapis.com) ficavam cacheadas 10s, então o retry de
# transporte da app re-tentava DENTRO desses 10s e batia no mesmo cache → esgotava as tentativas e
# "Calcular rota" só voltava após ~10s. =0 não cacheia a falha → cada retry re-resolve de verdade.
# TEM que ser na SECURITY PROPERTY: o -Dsun.net.inetaddr.negative.ttl é IGNORADO quando o
# java.security define a propriedade explicitamente. (A positiva fica no default 30s — está comentada.)
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

# Cap the heap against the container's cgroup memory limit so the JVM isn't OOM-killed by ECS
# (JDK default is 25%; 75% leaves headroom for metaspace/non-heap/HTTP client buffers).
# (O DNS negative-cache é desligado via java.security acima — system property não bastava.)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
