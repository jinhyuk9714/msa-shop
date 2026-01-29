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

## 2. 서비스 구성 (초기 버전)

각 서비스는 **독립 DB** 를 가진다. 통신은 우선 **REST + Resilience4j** 로 시작한다.

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

- **settlement-service** (2단계로 추가)
  - 책임: 일별/월별 정산, 판매자/카테고리별 매출 집계
  - 결제 완료 이벤트를 소비해서 집계 테이블을 관리하는 배치/서비스

예상 프로젝트 구조:

```text
msa-shop/
  docs/
    ARCHITECTURE.md
    API-SPEC.md
    FAILURE-SCENARIOS.md
  user-service/
  product-service/
  order-service/
  payment-service/
  settlement-service/   # 2단계에 추가
  docker-compose.yml
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
  - Spring Boot 3.x, Java 21
  - Spring Web, Spring Data JPA, Spring Boot Actuator
  - Resilience4j (CircuitBreaker, Retry)
- **인프라**
  - 로컬 단독 실행: H2 메모리 DB. Docker Compose 실행 시: **MySQL 8** (서비스별 DB: userdb, productdb, orderdb, paymentdb, settlementdb).
  - Docker Compose 로 MySQL + 각 서비스 기동.
- **인증**
  - user-service 에서 JWT 발급
  - order-service 에서는 JWT 파싱/검증만 수행  
    (또는 나중에 API Gateway 를 앞단에 두고 거기서 인증/인가 처리)

---

## 6. 문서 운영 방식

- 설계·시퀀스·스코프 등 상세한 내용은 이 문서(`docs/ARCHITECTURE.md`)를 기준으로 확장한다.
- 추가로 필요 시:
  - `docs/API-SPEC.md` – 각 서비스별 REST API 스펙 정리
  - `docs/FAILURE-SCENARIOS.md` – 장애/실패 시나리오와 대응 전략 정리
