FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy common lib first and build it
COPY gomirai-common-lib ./gomirai-common-lib
WORKDIR /workspace/gomirai-common-lib
RUN apk add --no-cache maven && mvn clean install -DskipTests

# Now build NotificationService
WORKDIR /workspace/NotificationService
COPY NotificationService/pom.xml .
COPY NotificationService/mvnw .
COPY NotificationService/.mvn ./.mvn


# Copy source and build
COPY NotificationService/src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/NotificationService/target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
