# Multi-stage build for Payment Service with common-lib support
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Build shared library first
COPY gomirai-common-lib ./gomirai-common-lib
WORKDIR /workspace/gomirai-common-lib
RUN apk add --no-cache maven && mvn clean install -DskipTests

# Build Payment Service
WORKDIR /workspace/PaymentSerivce
COPY PaymentSerivce/pom.xml .
COPY PaymentSerivce/mvnw .
COPY PaymentSerivce/.mvn ./.mvn
RUN ./mvnw dependency:go-offline -B

COPY PaymentSerivce/src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/PaymentSerivce/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]


