# syntax=docker/dockerfile:1
# 🚀 Docker BuildKit Optimized Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Install Maven only once
RUN apk add --no-cache maven

# 1. Build common-lib with cache
COPY gomirai-common-lib ./gomirai-common-lib
WORKDIR /workspace/gomirai-common-lib
RUN --mount=type=cache,target=/root/.m2 mvn clean install -DskipTests

# 2. Build Service with cache
WORKDIR /workspace/IdentityService
# Copy POM first to leverage dependency caching
COPY IdentityService/pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B

COPY IdentityService/src ./src
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

# 3. Runtime stage (Slim)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build /workspace/IdentityService/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-jar", "app.jar"]
