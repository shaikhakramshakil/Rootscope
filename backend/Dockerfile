FROM docker.io/maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM docker.io/eclipse-temurin:17-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/rootscope-0.1.0.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -sf http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
