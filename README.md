# MSA Shop

쇼핑몰 도메인 기반 **마이크로서비스 아키텍처(MSA)** 연습 프로젝트입니다.  
회원, 상품, 주문, 결제(정산 예정)를 여러 서비스로 나누어 구현합니다.

## 목표

- User / Product / Order / Payment / Settlement 로 서비스 경계 나누기
- 서비스 간 통신 (REST + Resilience4j)
- 주문/결제 흐름에서 SAGA·보상 트랜잭션 맛보기
- 결제 완료 이벤트 기반 정산/매출 집계 배치

## 기술 스택

- Java 21, Spring Boot 3.5, Spring Data JPA
- 로컬: H2. **Docker Compose**: API Gateway(8080) + MySQL 8 + RabbitMQ + 5서비스(8081~8085) + Prometheus(9090)·Grafana(3000)·Zipkin(9411).
- **api-gateway**: Spring Cloud Gateway, JWT 검증·X-User-Id 전달.
- user-service: Spring Security, JWT(HS256, JJWT)  
  order-service: Resilience4j (Retry, CircuitBreaker), Outbox 보상 스케줄러

## 빌드 및 실행

```bash
./gradlew build -x test
./gradlew :user-service:bootRun     # 8081
./gradlew :product-service:bootRun  # 8082
./gradlew :payment-service:bootRun  # 8084
./gradlew :order-service:bootRun    # 8083
```

네 서비스 기동 후 E2E:

```bash
./scripts/e2e-flow.sh
./scripts/e2e-failure-scenarios.sh   # 재고 부족(409) 등 실패 시나리오
```

**Docker Compose** (Gateway + MySQL + 5서비스):

```bash
docker-compose up --build -d
GATEWAY_URL=http://localhost:8080 ./scripts/e2e-flow.sh
```

- **Gradle Wrapper**: `gradle-wrapper.jar` 없으면 `gradle wrapper` 한 번 실행.
- 상세 절차·트러블슈팅: [`docs/RUN-LOCAL.md`](docs/RUN-LOCAL.md)

## 문서

| 문서                                                     | 설명                                       |
| -------------------------------------------------------- | ------------------------------------------ |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)           | 아키텍처·서비스 구성·주문 플로우·기술 스택 |
| [`docs/IMPLEMENTATION.md`](docs/IMPLEMENTATION.md)       | 구현 현황·모듈·API·E2E·Docker              |
| [`docs/RUN-LOCAL.md`](docs/RUN-LOCAL.md)                 | 로컬 실행 가이드·E2E 시나리오              |
| [`docs/API-SPEC.md`](docs/API-SPEC.md)                   | 서비스별 REST API 스펙 요약                |
| [`docs/FAILURE-SCENARIOS.md`](docs/FAILURE-SCENARIOS.md) | 장애/실패 시나리오와 대응 전략             |

## 모듈

- **api-gateway** (8080): 클라이언트 단일 진입점, 라우팅·JWT 검증·X-User-Id 전달.
- **user-service** (8081): 회원가입, 로그인(더미 토큰), GET /users/me, 409 중복 이메일
- **product-service** (8082): 상품/재고, `POST /internal/stocks/reserve`, `POST /internal/stocks/release`(보상), 테스트 상품 시딩
- **order-service** (8083): 주문 생성·조회, product/payment 연동, SAGA 보상(결제 실패 시 재고 복구), Outbox(결제 성공 후 주문 저장 실패 시 결제 취소·재고 복구)
- **payment-service** (8084): 가짜 PG 결제 승인, `POST /payments/{id}/cancel`(보상용). 결제 완료 시 RabbitMQ로 이벤트 발행
- **settlement-service** (8085): RabbitMQ에서 결제 완료 이벤트 구독, 일별 매출 집계(`GET /settlements/daily`)
- **OpenAPI**: 각 서비스 `/swagger-ui.html`, `/v3/api-docs` (springdoc-openapi 2.5.0)

## 테스트·배포

- **단위·통합 테스트**: `./gradlew test`. order/product/user/payment 서비스 통합 테스트는 Testcontainers(MySQL·RabbitMQ·MockWebServer) 사용(Docker 필요).
- **CI**: GitHub Actions — `main` 푸시/PR 시 `./gradlew test` 자동 실행. [`.github/workflows/ci.yml`](.github/workflows/ci.yml).
- **K8s**: [`k8s/README.md`](k8s/README.md) — 전체 스택(MySQL, RabbitMQ, 6개 앱, Secret, Ingress) 적용 순서·파일 구성. 배포 후 `kubectl port-forward svc/api-gateway 8080:8080` + `GATEWAY_URL=http://localhost:8080 ./scripts/e2e-flow.sh` 로 E2E 검증.
