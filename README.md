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
- H2 (로컬). Docker Compose로 4서비스 통합 실행 가능(포트 8081~8084).
- user-service: Spring Security, JWT(더미 토큰)  
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
```

**Docker Compose** (로컬 서버 없이 4서비스 한 번에):

```bash
docker-compose up --build -d
./scripts/e2e-flow.sh
```

- **Gradle Wrapper**: `gradle-wrapper.jar` 없으면 `gradle wrapper` 한 번 실행.
- 상세 절차·트러블슈팅: [`docs/RUN-LOCAL.md`](docs/RUN-LOCAL.md)

## 문서

| 문서 | 설명 |
|------|------|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | 아키텍처·서비스 구성·주문 플로우·기술 스택 |
| [`docs/IMPLEMENTATION.md`](docs/IMPLEMENTATION.md) | 구현 현황·API·E2E·최근 완료 작업 |
| [`docs/RUN-LOCAL.md`](docs/RUN-LOCAL.md) | 로컬 실행 가이드·E2E 시나리오 |

## 모듈

- **user-service** (8081): 회원가입, 로그인(더미 토큰), GET /users/me, 409 중복 이메일
- **product-service** (8082): 상품/재고, `POST /internal/stocks/reserve`, `POST /internal/stocks/release`(보상), 테스트 상품 시딩
- **order-service** (8083): 주문 생성·조회, product/payment 연동, SAGA 보상(결제 실패 시 재고 복구), Outbox(결제 성공 후 주문 저장 실패 시 결제 취소·재고 복구)
- **payment-service** (8084): 가짜 PG 결제 승인, `POST /payments/{id}/cancel`(보상용)
