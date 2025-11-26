FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Build common library first so DriverService can reuse it
COPY gomirai-common-lib ./gomirai-common-lib
WORKDIR /workspace/gomirai-common-lib
RUN apk add --no-cache maven && mvn clean install -DskipTests

# Build DriverService
WORKDIR /workspace/DriverService
COPY DriverService/pom.xml .
COPY DriverService/mvnw .
COPY DriverService/.mvn ./.mvn
RUN ./mvnw dependency:go-offline -B

COPY DriverService/src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/DriverService/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]

