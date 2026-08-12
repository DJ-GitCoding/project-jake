# Build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

# Trust the infra CA so outbound HTTPS to internal services validates.
COPY infra-ca.cr[t] /tmp/cactx/
RUN if [ -f /tmp/cactx/infra-ca.crt ]; then \
      keytool -importcert -noprompt -cacerts -storepass changeit \
        -alias icann-infra-ca -file /tmp/cactx/infra-ca.crt && \
      echo "Imported infra CA into cacerts"; \
    else echo "No infra CA staged; skipping cacerts import"; fi && \
    rm -rf /tmp/cactx

COPY --from=build /app/target/*.jar app.jar
RUN chown -R spring:spring /app
USER spring

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8000/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
