# One Dockerfile for every Java module: docker build --build-arg MODULE=services/order-service .
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests -pl ${MODULE} -am package

FROM eclipse-temurin:21-jre
ARG MODULE
RUN useradd --system --uid 1001 payflow
WORKDIR /app
COPY --from=build /src/${MODULE}/target/app.jar app.jar
USER payflow
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
