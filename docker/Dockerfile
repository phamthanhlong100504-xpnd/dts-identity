# ================================================================
# Multi-stage Dockerfile — Identity Service
# ================================================================
# Stage 1: Build ứng dụng bằng Maven
# ================================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build
# Copy Maven settings with mirror (for regions where Maven Central is blocked)
COPY docker/settings.xml /root/.m2/settings.xml

# Copy POM trước để cache Maven dependencies (layer tối ưu)
COPY pom.xml .
RUN mvn dependency:go-offline -B -q -s /root/.m2/settings.xml

# Copy source code và build
COPY src ./src
RUN mvn package -DskipTests -B -q -s /root/.m2/settings.xml

# ================================================================
# Stage 2: Runtime image (JRE alpine, tối thiểu)
# ================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user
RUN addgroup -S dts && adduser -S dts -G dts

WORKDIR /app

# Copy JAR từ stage builder
COPY --from=builder /build/target/*.jar app.jar

RUN chown -R dts:dts /app
USER dts

EXPOSE 8081

# Healthcheck
HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=40s \
    CMD wget -qO- http://localhost:8081/actuator/health || exit 1

# JVM options:
#   -XX:+UseZGC          : Z Garbage Collector (low latency)
#   -XX:MaxRAMPercentage  : Giới hạn heap theo % RAM container
#   -XX:+ExitOnOutOfMemoryError : Thoát ngay khi OOM (K8s sẽ restart)
ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
