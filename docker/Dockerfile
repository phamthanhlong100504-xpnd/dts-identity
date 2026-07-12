FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S dts && adduser -S dts -G dts

WORKDIR /app
COPY target/*.jar app.jar

RUN chown -R dts:dts /app
USER dts

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
