# 빌드 컨텍스트: 프로젝트 루트. docker build -f api-gateway/Dockerfile .
# Gradle 캐시 공유: DOCKER_BUILDKIT=1 (기본) 시 두 번째 빌드부터 훨씬 빠름.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY . .
RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    ./gradlew :api-gateway:build -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/api-gateway/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
