# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
RUN apk add --no-cache maven

COPY gomirai-common-lib ./gomirai-common-lib
WORKDIR /workspace/gomirai-common-lib
RUN --mount=type=cache,target=/root/.m2 mvn clean install -DskipTests

WORKDIR /workspace/DriverService
COPY DriverService/pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B
COPY DriverService/src ./src
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build /workspace/DriverService/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
