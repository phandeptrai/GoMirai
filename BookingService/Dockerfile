# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /scan

# Copy common lib
COPY gomirai-common-lib /scan/gomirai-common-lib
COPY BookingService /scan/BookingService

# Build Common Lib
WORKDIR /scan/gomirai-common-lib
RUN ../BookingService/mvnw clean install -DskipTests

# Build Service
WORKDIR /scan/BookingService
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /scan/BookingService/target/*.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]


