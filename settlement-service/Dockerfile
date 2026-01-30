# 빌드 컨텍스트: 프로젝트 루트. docker build -f settlement-service/Dockerfile .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY . .
RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    ./gradlew :settlement-service:build -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/settlement-service/build/libs/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
