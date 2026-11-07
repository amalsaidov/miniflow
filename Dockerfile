FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 miniflow
WORKDIR /app
COPY --from=build /workspace/target/miniflow-*.jar app.jar
RUN mkdir -p /app/data && chown -R miniflow /app
USER miniflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
