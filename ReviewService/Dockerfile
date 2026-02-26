# Stage 1: Build Common Lib & Service
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# 1. Copy và cài đặt gomirai-common-lib trước
COPY gomirai-common-lib ./gomirai-common-lib
WORKDIR /workspace/gomirai-common-lib
RUN apk add --no-cache maven && mvn clean install -DskipTests

# 2. Build ReviewService
WORKDIR /workspace/ReviewService
COPY ReviewService/pom.xml .
COPY ReviewService/mvnw .
COPY ReviewService/.mvn ./.mvn

COPY ReviewService/src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build /workspace/ReviewService/target/*.jar app.jar
EXPOSE 8089
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-jar", "app.jar"]