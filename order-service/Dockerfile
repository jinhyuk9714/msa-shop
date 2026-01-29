# 빌드 컨텍스트: 프로젝트 루트. docker build -f order-service/Dockerfile .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY . .
RUN ./gradlew :order-service:build -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/order-service/build/libs/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
