# Multi-stage build with common-lib support
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy common lib first and build it
COPY gomirai-common-lib ./gomirai-common-lib
WORKDIR /workspace/gomirai-common-lib
RUN apk add --no-cache maven && mvn clean install -DskipTests

# Now build PricingService
WORKDIR /workspace/PricingService
COPY PricingService/pom.xml .
COPY PricingService/mvnw .
COPY PricingService/.mvn ./.mvn


# Copy source and build
COPY PricingService/src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/PricingService/target/*.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
