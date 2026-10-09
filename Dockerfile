# Build stage: compile the jar with Maven
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml ./pom.xml
RUN mvn -q dependency:go-offline
COPY backend/src ./src
RUN mvn -q clean package -DskipTests

# Run stage: small Java runtime image
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# 60% of the container memory for the heap keeps the app inside Render's 512 MB free instance
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
