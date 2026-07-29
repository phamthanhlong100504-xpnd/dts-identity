# Stage 1: Build ứng dụng bằng Maven
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src src
RUN mvn package -DskipTests -B

# Stage 2: Runtime image (JRE alpine)
# FROM eclipse-temurin:21-jre-alpine AS runtime
FROM ibm-semeru-runtimes:open-21-jre AS runtime
WORKDIR /app
RUN addgroup -S dts && adduser -S dts -G dts
COPY --from=builder /app/target/*.jar app.jar
RUN chown -R dts:dts /app
USER dts
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
