# 빌드 컨텍스트: 프로젝트 루트. docker build -f payment-service/Dockerfile .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY . .
RUN ./gradlew :payment-service:build -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/payment-service/build/libs/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
