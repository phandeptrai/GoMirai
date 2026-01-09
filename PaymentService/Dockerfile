# Stage 1:# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /scan

# Copy common lib
COPY gomirai-common-lib /scan/gomirai-common-lib
COPY PaymentService /scan/PaymentService

# Build Common Lib
WORKDIR /scan/gomirai-common-lib
RUN ../PaymentService/mvnw clean install -DskipTests

# Build Service
WORKDIR /scan/PaymentService
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /scan/PaymentService/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]