# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# 1. Copy & Build Common Lib
COPY gomirai-common-lib ./gomirai-common-lib
WORKDIR /workspace/gomirai-common-lib
RUN apk add --no-cache maven && mvn clean install -DskipTests

# 2. Build MapService
WORKDIR /workspace/MapService
COPY MapService/pom.xml .
COPY MapService/mvnw .
COPY MapService/.mvn ./.mvn
RUN ./mvnw dependency:go-offline -B

# 3. Copy Source & Package
COPY MapService/src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy JAR từ Stage 1
COPY --from=build /workspace/MapService/target/*.jar app.jar

# Expose port (Internal only)
EXPOSE 8086

# Run
ENTRYPOINT ["java", "-jar", "app.jar"]



