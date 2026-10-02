FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml ./pom.xml
RUN mvn dependency:go-offline -B -q || true
COPY src ./src
RUN mvn package -DskipTests -B -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Trust the infra CA so outbound HTTPS to internal services validates.
COPY infra-ca.cr[t] /tmp/cactx/
RUN if [ -f /tmp/cactx/infra-ca.crt ]; then \
      keytool -importcert -noprompt -cacerts -storepass changeit \
        -alias icann-infra-ca -file /tmp/cactx/infra-ca.crt && \
      echo "Imported infra CA into cacerts"; \
    else echo "No infra CA staged; skipping cacerts import"; fi && \
    rm -rf /tmp/cactx

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8083
CMD ["java", "-jar", "app.jar"]