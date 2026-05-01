FROM gradle:8-jdk17-jammy AS builder
WORKDIR /workspace
COPY . .
RUN gradle :storage-service:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=builder /workspace/services/storage-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
