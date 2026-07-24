FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY build.gradle build.gradle
COPY settings.gradle settings.gradle
COPY gradle.properties gradle.properties
COPY src src

RUN chmod +x gradlew && ./gradlew build -Dquarkus.package.type=fast-jar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/quarkus-app /app

EXPOSE 8080
CMD ["java", "-jar", "quarkus-run.jar"]
