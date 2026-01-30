# 구현 현황 정리

> 현재 msa-shop 프로젝트에 **구현된 것 전체**를 한 문서로 정리한 요약입니다.  
> 상세 스펙은 [API-SPEC.md](API-SPEC.md), [IMPLEMENTATION.md](IMPLEMENTATION.md), [ARCHITECTURE.md](ARCHITECTURE.md) 참고.

---

## 1. 서비스 구성 (6개)

| 서비스 | 포트 | 역할 | DB |
|--------|------|------|-----|
| **api-gateway** | 8080 | 단일 진입점, 라우팅, JWT 검증, X-User-Id 전달, Rate Limit | - |
| **user-service** | 8081 | 회원가입, 로그인(JWT), GET /users/me | MySQL userdb |
| **product-service** | 8082 | 상품/재고, 검색·카테고리, 재고 예약·복구, Redis 캐시 | MySQL productdb |
| **order-service** | 8083 | 주문 생성·조회·취소, **장바구니** CRUD, product/payment 연동, SAGA·Outbox 보상 | MySQL orderdb |
| **payment-service** | 8084 | 가짜 PG 결제·취소, RabbitMQ 결제 완료 이벤트 발행 | MySQL paymentdb |
| **settlement-service** | 8085 | RabbitMQ 구독, 일별/월별 매출 집계 | MySQL settlementdb |

- **공통**: Java 21, Spring Boot 3.5, Spring Data JPA  
- **로컬 단독**: H2. **Docker/K8s**: MySQL 8 (서비스별 DB 1개씩).

---

## 2. API Gateway (8080)

| 항목 | 내용 |
|------|------|
| 라우팅 | `/users/**`, `/auth/**` → user-service, `/products/**` → product-service, `/orders/**` → order-service, `/payments/**` → payment-service, `/settlements/**` → settlement-service |
| 인증 | `/orders/**`, `/cart/**`, `/users/me` JWT 검증 후 `X-User-Id` downstream 전달. `/settlements/**` JWT 필요 여부는 `app.settlements.auth-required`(기본 false)로 설정 |
| Rate Limit | IP당 분당 횟수 제한(기본 120). `app.rate-limit.per-minute: 0`이면 비활성. `/actuator/health` 제외 |
| 프로파일 | `e2e` 프로파일 시 Rate Limit 비활성(application-e2e.yml) |

---

## 3. user-service (8081)

| API | 설명 |
|-----|------|
| POST /users | 회원가입. 중복 이메일 시 409 |
| POST /auth/login | 로그인 → JWT(HS256, JJWT) 발급 |
| GET /users/me | 내 정보 조회. Bearer JWT 필수 |

- 비밀번호 BCrypt. SecurityConfig: `/users`, `/auth/login`, `/actuator/**`, Swagger permitAll.

---

## 4. product-service (8082)

| API | 설명 |
|-----|------|
| GET /products | 목록·검색. Query: `name`, `category`, `minPrice`, `maxPrice` |
| GET /products/{id} | 상품 상세 |
| POST /internal/stocks/reserve | 재고 예약(내부). 재고 부족 시 success=false |
| POST /internal/stocks/release | 재고 복구(보상용) |

- **도메인**: Product에 `category` 필드. 시딩: 전자/생활/식품 등.
- **캐시**: 상품 목록·단건 Redis 캐시. 재고 reserve/release 시 evict. 로컬/Redis 미사용 시 `local` 프로파일로 인메모리 캐시.

---

## 5. order-service (8083)

| API | 설명 |
|-----|------|
| POST /orders | 주문 생성. JWT 또는 X-User-Id. productId, quantity, paymentMethod |
| GET /orders/{id} | 주문 단건 조회 |
| GET /orders/me | 내 주문 목록 |
| PATCH /orders/{id}/cancel | PAID 주문 취소(결제 취소 + 재고 복구) |
| POST /orders/from-cart | 장바구니 전체 주문(품목별 주문 생성 후 장바구니 비움) |
| GET /cart | 장바구니 조회 |
| POST /cart/items | 장바구니 추가(동일 상품 시 수량 합산, 재고 검증) |
| PATCH /cart/items/{productId} | 수량 변경(0이면 삭제) |
| DELETE /cart/items/{productId} | 항목 삭제 |
| DELETE /cart | 장바구니 비우기 |

- **플로우**: 상품 조회 → 재고 예약 → 결제 요청 → (실패 시 재고 복구) → 주문 저장.
- **Resilience4j**: product/payment 호출에 Retry, CircuitBreaker.
- **Outbox**: 결제 성공 후 주문 저장 실패 시 Outbox에 기록 → 스케줄러가 결제 취소·재고 복구.
- **예외**: 재고 부족 409, 결제 실패 402, 주문 없음 404, downstream 5xx → 502.

---

## 6. payment-service (8084)

| API | 설명 |
|-----|------|
| POST /payments | 결제 시도. amount≤0 시 실패 |
| POST /payments/{id}/cancel | 결제 취소(보상용) |

- 결제 승인 성공 시 **RabbitMQ** Topic `payment.events`(routing key `payment.completed`)로 이벤트 발행 → settlement-service 구독.

---

## 7. settlement-service (8085)

| API | 설명 |
|-----|------|
| GET /settlements/daily | 일별 매출. Query `date`(선택, yyyy-MM-dd). 없으면 최근 30일 목록 |
| GET /settlements/monthly | 월별 매출. Query `yearMonth`(선택, yyyy-MM). 없으면 최근 12개월 |

- RabbitMQ Queue `settlement.payment.completed` 구독 → 결제 완료 시 일별/월별 집계 갱신.

---

## 8. 인프라·실행 환경

### Docker Compose

- **포함**: api-gateway(8080), user/product/order/payment/settlement(8081~8085), MySQL 8(3306, 5 DB), RabbitMQ(5672, 15672), Redis(6379), Zipkin(9411), Prometheus(9090), Grafana(3000).
- **실행**: `docker-compose up --build -d`. E2E: `GATEWAY_URL=http://localhost:8080 ./scripts/e2e-flow.sh`.

### Kubernetes (k8s/)

- 매니페스트: `k8s/` — MySQL, RabbitMQ, 5 서비스, api-gateway, Secret, ConfigMap, Ingress.
- 적용 순서: [k8s/README.md](../k8s/README.md).

### Helm

- **차트**: `helm/msa-shop/` — 동일 스택 설치·업그레이드.
- **템플릿**: api-gateway, user/product/order/payment/settlement, mysql, rabbitmq, redis(선택), prometheus/grafana/zipkin(선택), secret, configmap-mysql-init, ingress, HPA(order-service).
- **기본값**: observability false, redis false(가벼운 설치). `--set observability.enabled=true`, `--set redis.enabled=true` 로 활성화.
- **배포**: `./scripts/helm-deploy.sh` 또는 `helm install/upgrade`. values-ghcr.yaml(ghcr.io 이미지), values-prod.yaml(운영 예시).

---

## 9. E2E 스크립트 (scripts/)

| 스크립트 | 용도 |
|----------|------|
| e2e-flow.sh | 6단계: 상품 목록 → 회원가입 → 로그인 → 주문 생성 → 주문 조회 → 당일 매출(settlement) |
| e2e-all-scenarios.sh | 8개 시나리오 일괄(상품·회원·주문·정산 등). Gateway 기준 |
| e2e-auth-scenarios.sh | 인증 관련 시나리오 |
| e2e-failure-scenarios.sh | 재고 부족(409) 등 실패 시나리오 |
| e2e-not-found-scenarios.sh | 404 시나리오 |
| e2e-order-scenarios.sh | 주문 관련 |
| e2e-product-search.sh | 상품 검색·카테고리 |
| e2e-rate-limit.sh | Rate Limit(429) 검증 |
| e2e-settlement-scenarios.sh | 정산 API |
| e2e-cart-scenarios.sh | 장바구니(추가→조회→수량 변경→삭제→비우기) |
| helm-deploy.sh | Helm 설치/업그레이드 래퍼 |

- 환경 변수: `GATEWAY_URL`, `SETTLEMENT_URL` 등. [RUN-LOCAL.md](RUN-LOCAL.md) 참고.

---

## 10. 테스트

| 종류 | 내용 |
|------|------|
| 단위 테스트 | user/product/order/payment 서비스 비즈니스 로직. `./gradlew test` |
| 통합 테스트 | order, product, user, payment — Testcontainers(MySQL, RabbitMQ, MockWebServer). `./gradlew test` |
| CI | `.github/workflows/ci.yml` — main 푸시/PR 시 JDK 21, `./gradlew test` |

---

## 11. CI/CD

| 항목 | 내용 |
|------|------|
| CI | GitHub Actions `ci.yml`: checkout, JDK 21, Gradle 캐시, `./gradlew test` |
| 이미지 빌드 | `build-images.yml`: main 푸시 시 6개 서비스 이미지 빌드 → ghcr.io 푸시. matrix로 서비스별 Dockerfile |
| Helm(ghcr) | values-ghcr.yaml로 ghcr.io 이미지 사용 배포 |

---

## 12. 관측성

| 항목 | 내용 |
|------|------|
| Actuator | 6개 서비스 `/actuator/health`, `/actuator/info`, `/actuator/prometheus` |
| Prometheus | docker/prometheus/prometheus.yml — 15초 스크래핑. Docker Compose·Helm(observability.enabled) |
| Grafana | Docker Compose·Helm. Prometheus 데이터소스 프로비저닝. admin/admin |
| Zipkin | api-gateway, product, order, payment Micrometer Tracing. Docker Compose·Helm |

---

## 13. OpenAPI / Swagger

- **적용**: user, product, order, payment, settlement — springdoc-openapi. `/api-docs.html`, `/v3/api-docs`.
- **API Gateway 통합 Swagger**: `GET http://gateway:8080/api-docs` → 단일 Swagger UI 페이지. 드롭다운에서 각 서비스(User/Product/Order/Payment/Settlement) OpenAPI 선택. `/api-docs/user-service` 등은 Gateway가 각 서비스 `/v3/api-docs`로 프록시. [OPENAPI.md](OPENAPI.md) 참고.

---

## 14. 프로파일·설정

| 프로파일 | 용도 |
|----------|------|
| default | 로컬 bootRun. H2, 로그 등 |
| prod | Docker/K8s. MySQL·RabbitMQ·env 필수. HikariCP 설정, 로그 축소 |
| local | product-service 로컬 실행 시 Redis 제외, 인메모리 캐시 |
| e2e | api-gateway Rate Limit 비활성 |

- 시크릿: [PROFILES-AND-SECRETS.md](PROFILES-AND-SECRETS.md), [SECRETS-OPERATIONS.md](SECRETS-OPERATIONS.md).

---

## 15. 문서 목록

| 문서 | 설명 |
|------|------|
| README.md | 프로젝트 개요, 빌드·실행, 문서/모듈 목록 |
| docs/README.md | 문서 인덱스 |
| docs/ARCHITECTURE.md | 아키텍처, 서비스 구성, 주문 플로우 |
| docs/IMPLEMENTATION.md | 구현 상세, 모듈·API·E2E·Docker·테스트 |
| docs/API-SPEC.md | REST API 스펙 요약 |
| docs/RUN-LOCAL.md | 로컬 실행, E2E, Helm, 트러블슈팅 |
| docs/OPENAPI.md | Swagger UI 접근·테스트 순서 |
| docs/FAILURE-SCENARIOS.md | 장애/실패 시나리오 |
| docs/PROFILES-AND-SECRETS.md | 프로파일·시크릿 |
| docs/SECRETS-OPERATIONS.md | 운영 시크릿 주입 |
| docs/NEXT-STEPS.md | 완료 상태·추천 순서·기능 확장 |
| docs/CI-IMAGES.md | CI 이미지 빌드·Helm(ghcr) |
| docs/K8S-EXPANSION.md | K8s 확장 계획 |
| docs/DOCKER-DESKTOP-K8S-CLEANUP.md | Docker Desktop K8s 정리 |
| k8s/README.md | K8s 매니페스트 적용 순서 |
| helm/README.md | Helm 차트 설치·업그레이드·HPA |

---

## 16. 미구현·선택 사항

| 항목 | 비고 |
|------|------|
| 장바구니 | ✅ order-service에 Cart API 구현 완료 (GET/POST/PATCH/DELETE /cart, /cart/items) |
| Argo CD | 사용하지 않음. 배포는 Helm 직접 사용 |
| 시크릿 도구 | Sealed Secrets, ESO, Vault 등은 운영 시 도입 권장 |

---

이 문서는 구현된 범위를 한눈에 보기 위한 요약입니다. API 상세·요청/응답 형식은 [API-SPEC.md](API-SPEC.md), 클래스·플로우 상세는 [IMPLEMENTATION.md](IMPLEMENTATION.md)를 참고하세요.
