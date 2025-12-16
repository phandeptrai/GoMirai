# Multi-stage build with common-lib support
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy common lib first and build it
COPY gomirai-common-lib ./gomirai-common-lib
WORKDIR /workspace/gomirai-common-lib
RUN apk add --no-cache maven && mvn clean install -DskipTests

# Now build BookingService
WORKDIR /workspace/BookingService
COPY BookingService/pom.xml .
COPY BookingService/mvnw .
COPY BookingService/.mvn ./.mvn
RUN ./mvnw dependency:go-offline -B

# Copy source and build
COPY BookingService/src ./src
RUN ./mvnw clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/BookingService/target/*.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]


