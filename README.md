# MSA Shop

쇼핑몰 도메인으로 **마이크로서비스(MSA)** 를 연습하는 프로젝트입니다.  
회원·상품·주문·결제·정산을 서비스별로 나누고, API Gateway·이벤트·보상 트랜잭션을 경험할 수 있습니다.

---

## 프로젝트에서 다루는 것

- **서비스 경계**: User, Product, Order, Payment, Settlement + API Gateway
- **서비스 간 통신**: REST, Resilience4j(Retry·CircuitBreaker)
- **주문/결제 흐름**: 재고 예약 → 결제 → 주문 저장. 실패 시 재고 복구(SAGA), 결제 성공 후 주문 저장 실패 시 Outbox로 보상
- **이벤트**: 결제 완료 시 RabbitMQ 발행 → Settlement가 일/월 매출 집계
- **기타**: JWT 인증, 장바구니(CRUD), 상품 검색·카테고리, Rate Limit, 관측성(Prometheus·Grafana·Zipkin)

**기술 스택**: Java 21, Spring Boot 3.5, Spring Data JPA. 로컬 H2 / Docker·K8s MySQL 8. API Gateway는 Spring Cloud Gateway.

---

## 빠르게 돌려보기

**테스트**

```bash
./gradlew test
```

**로컬에서 서비스만**

```bash
./gradlew :user-service:bootRun      # 8081
./gradlew :product-service:bootRun  # 8082
./gradlew :order-service:bootRun    # 8083
./gradlew :payment-service:bootRun  # 8084
./gradlew :settlement-service:bootRun # 8085
# + RabbitMQ (예: docker run -d -p 5672:5672 -p 15672:15672 rabbitmq:3-management)
./scripts/e2e-flow.sh
```

**Docker Compose로 전체 스택** (Gateway + MySQL + RabbitMQ + 6개 앱)

```bash
docker-compose up --build -d
GATEWAY_URL=http://localhost:8080 ./scripts/e2e-flow.sh
```

첫 빌드는 10~20분 걸릴 수 있고, 두 번째부터는 캐시로 빨라집니다.  
상세·트러블슈팅은 [docs/RUN-LOCAL.md](docs/RUN-LOCAL.md) 참고.

---

## 서비스 구성

| 서비스 | 포트 | 역할 |
|--------|------|------|
| **api-gateway** | 8080 | 단일 진입점, 라우팅·JWT 검증·Rate Limit |
| **user-service** | 8081 | 회원가입, 로그인(JWT), GET /users/me |
| **product-service** | 8082 | 상품/재고, 검색·카테고리, 재고 예약·복구, 캐시 |
| **order-service** | 8083 | 주문 생성·조회·취소, **장바구니** CRUD, SAGA·Outbox 보상 |
| **payment-service** | 8084 | 가짜 PG 결제·취소, RabbitMQ 이벤트 발행 |
| **settlement-service** | 8085 | RabbitMQ 구독, 일/월 매출 집계 |

Gateway 경유 시 `http://localhost:8080` 하나로 위 API를 모두 호출할 수 있고, `/api-docs` 에서 통합 Swagger UI를 볼 수 있습니다.

---

## 테스트·배포

- **E2E**: `./scripts/e2e-flow.sh`(기본 흐름), `./scripts/e2e-all-scenarios.sh`(9개 시나리오). Gateway 쓰려면 `GATEWAY_URL=http://localhost:8080` 설정.
- **CI**: push/PR 시 단위·통합 테스트 후 **E2E**(Docker Compose + e2e-flow.sh) 자동 실행. [.github/workflows/ci.yml](.github/workflows/ci.yml)
- **이미지**: main 푸시 시 6개 서비스 이미지 빌드 → ghcr.io. [.github/workflows/build-images.yml](.github/workflows/build-images.yml)
- **K8s/Helm**: [helm/README.md](helm/README.md). `./scripts/helm-deploy.sh` 로 설치·업그레이드.

---

## 더 보기

- **시작·실행**: [RUN-LOCAL.md](docs/RUN-LOCAL.md) — 로컬/Docker/E2E 순서, 트러블슈팅
- **아키텍처·API**: [ARCHITECTURE.md](docs/ARCHITECTURE.md), [API-SPEC.md](docs/API-SPEC.md), [IMPLEMENTED-SUMMARY.md](docs/IMPLEMENTED-SUMMARY.md)
- **장애·설정**: [FAILURE-SCENARIOS.md](docs/FAILURE-SCENARIOS.md), [PROFILES-AND-SECRETS.md](docs/PROFILES-AND-SECRETS.md)
- **배포·다음 단계**: [CI-IMAGES.md](docs/CI-IMAGES.md), [helm/README.md](helm/README.md), [NEXT-STEPS.md](docs/NEXT-STEPS.md)
- **문서 전체 목차**: [docs/README.md](docs/README.md)
