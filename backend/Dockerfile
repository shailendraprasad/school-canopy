FROM gradle:8.11.1-jdk21-alpine AS build
WORKDIR /app
COPY build.gradle build.gradle
COPY settings.gradle settings.gradle
COPY gradle.properties gradle.properties
COPY src src

RUN gradle build -Dquarkus.package.type=fast-jar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/quarkus-app /app

EXPOSE 8080
CMD ["java", "-jar", "quarkus-run.jar"]
