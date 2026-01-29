## 쇼핑몰 MSA 프로젝트 아키텍처

> 이 문서는 타임딜 프로젝트 이후 다음 사이드 프로젝트로 진행할  
> **“쇼핑몰 + 결제/정산 MSA”** 아이디어를 정리한 것입니다.  
> `msa-shop` 레포의 아키텍처 기준 문서입니다.

---

## 1. 목표

- 간단한 쇼핑몰 도메인을 기반으로 **여러 개의 마이크로서비스**를 설계/구현한다.
- 특히 다음과 같은 포인트를 경험하는 것이 목표:
  - 서비스 경계 나누기 (User / Product / Order / Payment / Settlement)
  - 서비스 간 통신 (REST + Resilience4j)
  - 주문/결제 흐름에서의 **SAGA/보상 트랜잭션** 개념 맛보기
  - 결제 완료 이벤트 기반 **정산/매출 집계** 배치 설계

---

## 2. 서비스 구성 (현재 구현)

각 서비스는 **독립 DB** 를 가진다. 클라이언트는 **API Gateway(8080)** 단일 진입점 사용. 서비스 간 통신: **REST + Resilience4j**, 결제 완료 → 정산은 **RabbitMQ** 이벤트.

- **user-service**
  - 책임: 회원, 권한(Role), 로그인(JWT 발급)
  - 주요 API:
    - `POST /users` – 회원가입
    - `POST /auth/login` – 로그인(JWT 발급)
    - `GET /users/me` – 내 정보 조회

- **product-service**
  - 책임: 상품/재고 관리
  - 주요 API:
    - `GET /products` – 상품 목록
    - `GET /products/{id}` – 상품 상세
    - `POST /internal/stocks/reserve` – 재고 예약/차감 (order-service 내부 호출용)

- **order-service**
  - 책임: 주문 생성/조회, 주문 상태 관리
  - 주요 API:
    - `POST /orders` – 주문 생성 (클라이언트 진입점)
    - `GET /orders/{id}` – 주문 단건 조회
    - `GET /orders/me` – 내 주문 목록
  - 내부적으로 user-service, product-service, payment-service 와 통신

- **payment-service**
  - 책임: “가짜 PG” 역할 – 결제 승인/취소 상태 관리
  - 주요 API:
    - `POST /payments` – 결제 시도
    - `POST /payments/{id}/cancel` – 결제 취소

- **settlement-service** (2단계로 추가) ✓
  - 책임: 일별/월별 매출 집계
  - **RabbitMQ**에서 결제 완료 이벤트 구독(Queue `settlement.payment.completed`) → `recordPaymentCompleted` 호출. 배치 Job(일별 row 보정, 월별 집계).

- **api-gateway** (3단계로 추가) ✓
  - 책임: 클라이언트 단일 진입점(8080). `/users/**`, `/auth/**`, `/products/**`, `/orders/**` 라우팅.
  - `/orders/**`, `/users/me` 요청 시 JWT 검증 후 `X-User-Id` 헤더로 downstream 전달.

현재 프로젝트 구조:

```text
msa-shop/
  docs/
    ARCHITECTURE.md
    IMPLEMENTATION.md
    RUN-LOCAL.md
    API-SPEC.md
    FAILURE-SCENARIOS.md
  api-gateway/
  user-service/
  product-service/
  order-service/
  payment-service/
  settlement-service/
  docker-compose.yml   # api-gateway, RabbitMQ, MySQL, 5서비스
```

---

## 3. 주문 플로우 (기본 버전)

`POST /orders` 요청이 들어왔을 때의 흐름:

1. **클라이언트 → order-service**
   - `POST /orders`
   - Body 예시: `{ "productId": 1, "quantity": 2, "paymentMethod": "CARD" }`
   - Authorization 헤더의 JWT 에서 `userId` 추출

2. **order-service → product-service**
   - `POST /internal/stocks/reserve`
   - 요청: `{ userId, productId, quantity }`
   - 동작: 재고 확인 및 차감
   - 응답: `{ success: true/false, reason, remainingStock }`

3. **order-service → payment-service**
   - `POST /payments`
   - 요청: `{ userId, orderTempId(or productId+amount), paymentMethod }`
   - 동작: 결제 시뮬레이션 (성공/실패 기준은 간단한 rule 또는 랜덤)
   - 응답: `{ success: true/false, paymentId, reason }`

4. **모든 단계 성공 시**
   - order-service DB 에 주문 레코드 INSERT (상태: `PAID`)
   - 클라이언트에 주문 상세 정보 반환

5. **실패 케이스**
   - 2번(재고 부족) 실패 → 주문 실패 응답 (`409 CONFLICT` 등)
   - 3번(결제 실패) 실패 → 재고 복구 후 주문 실패 응답 (`402 PAYMENT_REQUIRED` 등)
   - **4번(주문 저장) 실패**(결제는 성공): order-service Outbox에 보상 이벤트 기록 → 스케줄러가 결제 취소·재고 복구 수행 (2단계 구현 완료. 상세 `docs/IMPLEMENTATION.md`)

---

## 4. 단계별 목표 스코프

### 4.1 1단계 – 기본 흐름 완성

- 서비스 4개(user / product / order / payment) 기본 뼈대 + Docker Compose
- 주문 플로우가 **성공/실패 시 올바르게 끝나는 것**에 집중
  - 재고 부족 시 적절한 4xx
  - 결제 실패 시 적절한 4xx/5xx
- `order-service` → `product-service` / `payment-service` 호출에  
  **Resilience4j CircuitBreaker/Retry** 1회씩 적용 (간단한 설정)

### 4.2 2단계 – 깊이 파기

- **트랜잭션 일관성 이슈 다루기** ✓ 구현됨
  - 결제 성공 후 order DB 저장 실패 시: order-service Outbox 테이블에 보상 이벤트 기록 → `OutboxProcessor` 스케줄러가 결제 취소·재고 복구 호출. 상세는 `docs/IMPLEMENTATION.md` §2단계 Outbox.
- **settlement-service 추가**
  - payment-service 에서 “결제 완료 이벤트” 발행
  - settlement-service 가 이벤트를 소비하여 판매자/카테고리별 매출 집계
  - 일별/월별 집계 배치(Job) 설계 및 간단 구현

---

## 5. 기술 스택

- **공통**
  - Spring Boot 3.5, Java 21
  - Spring Web, Spring Data JPA, Spring Boot Actuator
  - Resilience4j (CircuitBreaker, Retry) — order-service
- **인프라**
  - 로컬 단독: H2 메모리 DB. **Docker Compose**: **MySQL 8**(5개 DB) + **RabbitMQ**(5672, 15672) + api-gateway + 5서비스.
- **인증**
  - **API Gateway**: `/orders/**`, `/users/me` 요청 시 JWT 검증 후 `X-User-Id` downstream 전달. user-service와 동일 `app.jwt.secret` 사용.
  - **user-service**: JWT 발급(JJWT HS256), BCrypt 비밀번호.
  - **order-service / user-service**: Gateway 경유 시 `X-User-Id` 사용, 직접 호출 시 `Authorization: Bearer` JWT 파싱.
- **이벤트**
  - **RabbitMQ**: 결제 완료 이벤트 — payment-service → Topic `payment.events` → settlement-service Queue `settlement.payment.completed` 구독.

---

## 6. 문서 운영 방식

- 설계·시퀀스·스코프 등 상세한 내용은 이 문서(`docs/ARCHITECTURE.md`)를 기준으로 확장한다.
- 추가로 필요 시:
  - `docs/API-SPEC.md` – 각 서비스별 REST API 스펙 정리
  - `docs/FAILURE-SCENARIOS.md` – 장애/실패 시나리오와 대응 전략 정리
