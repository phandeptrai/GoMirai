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
COPY --from=build /workspace/ReviewService/target/*.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]