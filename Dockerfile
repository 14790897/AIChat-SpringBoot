FROM node:22-slim AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
COPY --from=frontend /app/src/main/resources/static src/main/resources/static
RUN chmod +x ./gradlew && ./gradlew bootJar -x test -x buildFrontend -x npmInstall --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
