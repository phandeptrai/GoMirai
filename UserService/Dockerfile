# Multi-stage build with common-lib support
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy common lib first and build it
COPY gomirai-common-lib ./gomirai-common-lib
WORKDIR /workspace/gomirai-common-lib
RUN apk add --no-cache maven && mvn clean install -DskipTests

# Now build UserService
WORKDIR /workspace/UserService
COPY UserService/pom.xml .
COPY UserService/mvnw .
COPY UserService/.mvn ./.mvn

# Copy source and build
COPY UserService/src ./src
RUN ./mvnw clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/UserService/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
