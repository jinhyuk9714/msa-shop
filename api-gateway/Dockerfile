# 빌드 컨텍스트: 프로젝트 루트. docker build -f api-gateway/Dockerfile .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY . .
RUN ./gradlew :api-gateway:build -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/api-gateway/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
