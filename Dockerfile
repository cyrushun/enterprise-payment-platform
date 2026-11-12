# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package && \
    if jar tf target/enterprise-payment-platform-0.1.0-SNAPSHOT.jar | grep -Eq '^BOOT-INF/classes/keys/jwt-(private|public)\.pem$'; then \
      echo "ERROR: development JWT key material was packaged into the runtime JAR." >&2; exit 1; \
    fi

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S -g 10001 payment && adduser -S -u 10001 -G payment -h /app payment
WORKDIR /app
COPY --from=build --chown=payment:payment /workspace/target/enterprise-payment-platform-0.1.0-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
