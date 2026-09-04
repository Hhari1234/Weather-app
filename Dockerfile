# multi-stage build for a Spring Boot JAR
# stage 1: build the application
FROM maven:3.9-eclipse-temurin-24 AS builder

WORKDIR /app

# copy pom.xml first to leverage Docker layer caching
COPY pom.xml ./
COPY mvnw ./
COPY .mvn .mvn/

# download dependencies (cached if pom.xml hasn't changed)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# copy source code
COPY src ./src

# build the executable JAR (skip tests during build, they run separately)
RUN ./mvnw -B -DskipTests package

# stage 2: runtime image
FROM eclipse-temurin:24-jre-alpine

WORKDIR /app

# create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# copy the JAR from the builder stage
COPY --from=builder /app/target/weather-app.jar app.jar

# switch to non-root user
USER spring:spring

# expose the default port
EXPOSE 8080

# health check - Render uses this to know when the app is ready
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# run the application
# PORT is injected by Render; bind to 0.0.0.0 as required by Render
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
