FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /app/target/contract-agent-0.1.0.jar /app/contract-agent.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/contract-agent.jar"]

