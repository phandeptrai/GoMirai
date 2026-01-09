# Stage 1: Build Common Lib & Payment Service
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# 1. Copy và cài đặt gomirai-common-lib 
COPY gomirai-common-lib ./gomirai-common-lib
WORKDIR /workspace/gomirai-common-lib
RUN apk add --no-cache maven && mvn clean install -DskipTests

# 2. Build PaymentService
WORKDIR /workspace/PaymentService
COPY PaymentService/pom.xml .
COPY PaymentService/mvnw .
COPY PaymentService/.mvn ./.mvn

COPY PaymentService/src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/PaymentService/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]