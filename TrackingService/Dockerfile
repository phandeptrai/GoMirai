# Multi-stage build with common-lib support
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# 1. Copy & Build Common Lib
COPY gomirai-common-lib ./gomirai-common-lib
WORKDIR /workspace/gomirai-common-lib
RUN apk add --no-cache maven && mvn clean install -DskipTests

# 2. Build TrackingService
WORKDIR /workspace/TrackingService
COPY TrackingService/pom.xml .
COPY TrackingService/mvnw .
COPY TrackingService/.mvn ./.mvn

# 3. Copy Source & Package
COPY TrackingService/src ./src
RUN ./mvnw clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build /workspace/TrackingService/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "app.jar"]
