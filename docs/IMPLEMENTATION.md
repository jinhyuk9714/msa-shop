## 구현 현황 요약

### 최근 완료 작업

- **1단계**: Gradle 멀티 모듈, Wrapper, user/product/order/payment 4서비스, E2E `scripts/e2e-flow.sh`, 필수 단위 테스트. order-service 예외/상태코드(409·402·404·401), Resilience4j, GET /users/me, GET /orders/me. SAGA 보상(결제 실패 시 재고 복구), product `POST /internal/stocks/release`. order-service 테스트: paymentFails MockWebServer 전환, mockwebserver 의존성.
- **Docker Compose**: Dockerfile 4개(멀티 모듈 루트 빌드), docker-compose.yml(8081~8084), order-service 환경변수로 product/payment 호스트명 연결. E2E: `./scripts/e2e-flow.sh`. 상세 `docs/RUN-LOCAL.md` §6.
- **2단계(Outbox·보상)**: payment-service `POST /payments/{id}/cancel` 추가. order-service Outbox 테이블(`outbox_events`), 결제 성공 후 주문 저장 실패 시 `OutboxService.publishOrderSaveFailed` → `OutboxProcessor` 스케줄러(5s)가 결제 취소·재고 복구 수행. 테스트: `orderSaveFailsThenOutboxPublished` 추가.
- **settlement-service**: 결제 완료 이벤트 수신(POST /internal/events/payment-completed), 일별 매출 집계(DailySettlement). payment-service가 결제 승인 후 settlement 호출. Docker Compose에 settlement-service(8085) 추가, payment-service에 SETTLEMENT_SERVICE_BASE_URL 설정.

---

### 공통

- **언어/런타임**
  - Java 21
- **프레임워크**
  - Spring Boot 3.5.0
  - Spring Web, Spring Data JPA
  - (user-service) Spring Security
  - (order-service) Resilience4j (`@Retry`, `@CircuitBreaker`)
- **DB**
  - 로컬 단독 실행: 각 서비스별 H2 메모리 DB (MySQL MODE). **Docker Compose 실행 시**: MySQL 8 한 컨테이너에 서비스별 DB(userdb, productdb, orderdb, paymentdb, settlementdb) 사용.

---

## 모듈 구조

- `user-service`
- `product-service`
- `order-service`
- `payment-service`

각 서비스는 독립적인 Spring Boot 애플리케이션이며, 루트 Gradle 멀티 모듈로 관리된다.

---

## user-service

- **포트**: 8081
- **주요 책임**: 회원 관리, 로그인(JWT 발급). 비밀번호 BCrypt 해싱 저장·검증.

### 도메인

- `User`
  - `id`, `email`, `password`, `name`
  - 이메일 유니크 제약

### API

- `POST /users`
  - 회원가입
  - Request: `{ "email", "password", "name" }`
  - Response: `{ "id", "email", "name" }`
  - 이미 존재하는 이메일: **409 CONFLICT** + JSON `{ "error": "CONFLICT", "message": "이미 존재하는 이메일입니다." }`  
    (`DuplicateEmailException` → `UserControllerAdvice`)

- `POST /auth/login`
  - 로그인(검증 후 JWT 액세스 토큰 발급, JJWT HS256)
  - Request: `{ "email", "password" }`
  - Response: `{ "accessToken": "<JWT>" }` (payload: sub=email, userId, exp)

- `GET /users/me`
  - 내 정보 조회. `Authorization: Bearer <JWT>` 필수. JWT 검증 후 userId로 DB 조회.
  - Response: `{ "id", "email", "name" }`. 토큰 오류/만료 시 401, 사용자 없음 시 404.

### 보안 설정

- `SecurityConfig`
  - `/users`, `/auth/login`, `/actuator/**`, `/h2-console/**` → permitAll
  - 그 외 요청은 인증 필요
  - CSRF 비활성화, H2 콘솔 사용을 위해 frameOptions 비활성화

---

## product-service

- **포트**: 8082
- **주요 책임**: 상품/재고 조회 및 재고 예약

### 도메인

- `Product`
  - `id`, `name`, `price`, `stockQuantity`
  - `decreaseStock(quantity)` 로 재고 차감 (리플렉션 제거)

### API

- `GET /products`
  - 상품 목록 조회
  - Response: `[{ "id", "name", "price", "stockQuantity" }, ...]`

- `GET /products/{id}`
  - 상품 상세 조회
  - Response: `{ "id", "name", "price", "stockQuantity" }`

- `POST /internal/stocks/reserve`
  - 주문 시 사용되는 **재고 예약/차감** 내부 API
  - Request: `{ "userId", "productId", "quantity" }`
  - Response:
    - 성공: `{ "success": true, "reason": "성공", "remainingStock": <남은 재고> }`
    - 실패(재고 부족): `{ "success": false, "reason": "재고 부족", "remainingStock": <현재 재고> }`

- `POST /internal/stocks/release`
  - **SAGA 보상용**: 재고 예약 후 결제 실패/오류 시 재고 복구. order-service 내부 호출.
  - Request: `{ "userId", "productId", "quantity" }`
  - Response: 200 + 빈 JSON 등 (성공 여부만 사용).

### 테스트 데이터

- `ProductDataLoader` (CommandLineRunner): 기동 시 상품 A(1만원/100), B(2.5만원/50), C(5천원/5) 자동 등록.

---

## payment-service

- **포트**: 8084
- **주요 책임**: 결제 승인 상태 관리(간단한 가짜 PG)

### 도메인

- `Payment`
  - `id`, `userId`, `amount`, `paymentMethod`, `status`, `createdAt`
- `PaymentStatus`
  - `APPROVED`, `CANCELED` (보상·취소 시 사용)

### API

- `POST /payments`
  - 결제 시도
  - Request: `{ "userId", "amount", "paymentMethod" }`
  - 간단한 룰:
    - `amount <= 0` → 실패
  - Response:
    - 성공: `{ "success": true, "paymentId": <id>, "reason": "APPROVED" }`
    - 실패: `{ "success": false, "paymentId": null, "reason": "<에러 메시지>" }`

- `POST /payments/{id}/cancel`
  - 결제 취소(보상용). order-service Outbox 스케줄러 또는 동기 보상에서 호출.
  - APPROVED인 결제만 CANCELED로 변경. 200 OK. 해당 결제 없으면 404.

---

## order-service

- **포트**: 8083
- **주요 책임**: 주문 생성 및 조회, 외부 서비스 호출 조합

### 도메인

- `Order`
  - `id`, `userId`, `productId`, `quantity`, `totalAmount`, `status`, `createdAt`
- `OrderStatus`
  - `PAID`, `FAILED` (현재는 성공 시 PAID 로 저장)

### 외부 연동

- `product-service.base-url`: `http://localhost:8082`
- `payment-service.base-url`: `http://localhost:8084`
- `OrderService` 내부에서 `RestTemplate` + Resilience4j(`@Retry`, `@CircuitBreaker`) 사용

### 주문 플로우 (`POST /orders`)

1. **인증 정보에서 userId 추출**
   - `Authorization: Bearer <JWT>`

- user-service가 발급한 **JWT**를 order-service에서 동일 secret(app.jwt.secret)으로 검증·userId 추출

2. **상품 정보 조회**
   - `GET product-service /products/{id}`
   - 가격 정보를 가져와 `totalAmount = price * quantity` 계산

3. **재고 예약**
   - `POST product-service /internal/stocks/reserve`
   - 실패(`success=false`) 시 주문 실패로 간주하고 예외 발생

4. **결제 요청**
   - `POST payment-service /payments`
   - 실패(`success=false`) 시 **보상**: `POST product-service /internal/stocks/release` 호출 후 `PaymentFailedException` (402).
   - `requestPayment` 내부 예외(네트워크 등) 시에도 동일하게 재고 복구 후 예외 전파.

5. **주문 저장**
   - 모든 단계가 성공하면 `Order(status = PAID)` 엔티티를 저장

### API

- `POST /orders`
  - Request 헤더:
    - `Authorization: Bearer <JWT>`
  - Body: `{ "productId", "quantity", "paymentMethod" }`
  - 성공 시:
    - 201 Created + 주문 정보 반환
  - 실패 시:
    - 현재는 `IllegalStateException` 시 `409 CONFLICT` 수준으로 메시지 반환

- `GET /orders/{id}`
  - 주문 단건 조회. 없으면 404.

- `GET /orders/me`
  - 내 주문 목록. `Authorization: Bearer <JWT>` 필수. `createdAt` 내림차순.

order-service `OrderControllerAdvice`: 재고 부족 → 409, 결제 실패 → 402, 주문 없음 → 404, 토큰 오류 → 401.

---

## 1번: 로컬 실행 및 E2E 검증

**상세 절차**: `docs/RUN-LOCAL.md` 참고.

- **Gradle**: 루트 `build.gradle`(Groovy), `gradlew` + `gradle/wrapper` 사용. `tasks.withType(Test)` 등 Groovy 문법.
- **테스트 상품**: `product-service` `ProductDataLoader` 로 상품 A/B/C 자동 등록.
- **E2E 스크립트**: `./scripts/e2e-flow.sh` — 상품 목록 → 회원가입(409 시 스킵) → 로그인 → 주문 생성 → 주문 조회.  
  macOS 호환(`sed '$d'` 등), 중복 가입 시 409 처리 후 로그인으로 진행.

---

## 실행 순서(예시)

1. **user-service 실행 후 회원가입/로그인**
   - `POST /users` 로 회원가입
   - `POST /auth/login` 으로 로그인 후 `accessToken`(JWT) 획득

2. **product-service**
   - `ProductDataLoader` 로 테스트 상품 자동 등록 (별도 입력 불필요)

3. **order-service로 주문 생성**
   - 헤더: `Authorization: Bearer <accessToken>`
   - `POST /orders`  
     Body: `{ "productId": 1, "quantity": 2, "paymentMethod": "CARD" }`

4. **payment-service는 order-service에서 내부적으로 호출**
   - 별도 직접 호출 없이 `/orders` 호출만으로 결제까지 시뮬레이션됨

---

## 기타 정리

- **order-service**: Resilience4j `resilience4j-spring-boot3:2.3.0` 명시, `productService`/`paymentService` Retry(waitDuration 200ms)·CircuitBreaker(failureRateThreshold 50, waitDurationInOpenState 5s) 설정.
- **payment-service**: `application.yml` (포트 8084, H2).

---

## 테스트 (필수 단위 테스트)

- **user-service** `UserServiceTest`: 회원가입 성공/중복 이메일(`DuplicateEmailException`), 로그인 성공/없는 이메일/비밀번호 오류.
- **product-service** `ProductTest`: `Product.decreaseStock` 성공, 수량 0·음수·재고 초과 시 `IllegalArgumentException`.
- **payment-service** `PaymentServiceTest`: `approve` 성공, 금액 ≤ 0 시 `IllegalArgumentException`.
- **order-service** `OrderServiceTest`: `createOrder` 성공(외부 HTTP Mock), 재고 예약 실패(`InsufficientStockException`), **결제 실패 시 `PaymentFailedException` 및 재고 복구 호출** 검증, `getOrder` 성공/미존재.

실행: `./gradlew test`. order-service는 `success`/`reserveFails`에 `MockRestServiceServer`, **`paymentFails`에 OkHttp `MockWebServer`** 사용(경로별 Dispatcher로 product/payment/release 스텁, 순서·매칭 이슈 회피). `mockwebserver` 의존성: `order-service/build.gradle` `testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")`.

---

## API Gateway (3단계)

- **포트**: 8080. 클라이언트 단일 진입점.
- **역할**: Spring Cloud Gateway로 `/users/**`, `/auth/**` → user-service, `/products/**` → product-service, `/orders/**` → order-service 라우팅.
- **JWT 검증**: `/orders/**`, `/users/me` 요청 시 `Authorization: Bearer` JWT 검증 후 `X-User-Id` 헤더로 downstream 전달. user-service와 동일 `app.jwt.secret` 사용.
- **order-service / user-service**: `X-User-Id` 헤더가 있으면 사용, 없으면 기존처럼 JWT 파싱(직접 호출·E2E 호환).

---

## Docker Compose

- **docker-compose.yml**: **api-gateway(8080)** + MySQL 8 + user / product / order / payment / settlement. 루트에서 `docker-compose up --build -d` 실행.
- **api-gateway**: user·product·order 서비스 기동 후 기동. `USER_SERVICE_URI`, `PRODUCT_SERVICE_URI`, `ORDER_SERVICE_URI` 로 라우팅.
- **MySQL**: 이미지 `mysql:8`, 초기화 스크립트로 5개 DB 생성. MySQL healthcheck 통과 후 서비스 기동.
- **E2E**: Gateway 경유 `GATEWAY_URL=http://localhost:8080 ./scripts/e2e-flow.sh`. 직접 호출 `./scripts/e2e-flow.sh`. 상세는 `docs/RUN-LOCAL.md` §6.

---

## 2단계: Outbox + 보상 (결제 성공 후 주문 저장 실패)

- **payment-service**: `POST /payments/{id}/cancel` — 결제 취소(보상용). order-service 또는 Outbox 스케줄러가 호출.
- **order-service**:
  - **Outbox 테이블** (`outbox_events`): 이벤트 타입·payload(JSON)·상태(PENDING/PROCESSED/FAILED). 주문 저장 실패 시 `REQUIRES_NEW` TX로 보상 이벤트만 기록.
  - **createOrder**: 결제 성공 후 `orderRepository.save()` 실패 시 `OutboxService.publishOrderSaveFailed(paymentId, userId, productId, quantity)` 호출 → 스케줄러가 처리.
  - **OutboxProcessor** (`@Scheduled(fixedDelay = 5s)`): PENDING 이벤트 조회 → `ORDER_SAVE_FAILED`면 결제 취소 + 재고 복구 호출 → PROCESSED 처리.
- **설정**: `app.outbox.process-interval` (기본 5000ms). `@EnableScheduling` 적용.

---

## settlement-service

- **포트**: 8085
- **역할**: 결제 완료 이벤트 수신(RabbitMQ 구독) → 일별 매출 집계(daily_settlements 테이블).

### API

- 결제 완료 이벤트는 **RabbitMQ**로 수신. Exchange `payment.events`, Queue `settlement.payment.completed`, Routing Key `payment.completed`. `PaymentCompletedListener`가 메시지 수신 시 `recordPaymentCompleted` 호출.
- `GET /settlements/daily?date=yyyy-MM-dd`  
  특정 일자 집계. 없으면 404.
- `GET /settlements/daily`  
  date 없으면 최근 일별 집계 목록(최대 30, 날짜 내림차순).

### payment-service 연동 (이벤트 드리븐)

- 결제 승인(`approve`) 성공 후 **RabbitMQ** Topic Exchange `payment.events`로 `PaymentCompletedEvent`(paymentId, userId, amount, paidAt) 발행. settlement-service가 Queue `settlement.payment.completed`에서 구독해 매출 집계. 발행 실패 시 로그만 남기고 결제는 성공 유지.

---

## 앞으로의 확장 아이디어 (2단계용 메모)

- ~~Outbox 패턴 도입 및 주문/결제 보상 트랜잭션 구현~~ (위에서 구현)
- ~~결제 완료 이벤트 발행 및 settlement-service 추가~~ (위에서 구현)
- ~~MySQL 전환 및 Docker Compose로 서비스+DB 통합 실행~~ (MySQL 8 + 5 DB, 환경변수로 연결)
- ~~user-service에 실제 JWT 발급/검증 로직 추가 및 order-service에서 JWT 파싱으로 전환~~ (JJWT HS256, app.jwt.secret 공유)
- ~~user-service 비밀번호 BCrypt 해싱~~ (회원가입 시 encode, 로그인 시 matches)
- ~~3단계: API Gateway~~ (Spring Cloud Gateway 8080, 라우팅·JWT 검증·X-User-Id 전달)
- ~~이벤트 드리븐 (RabbitMQ)~~ (결제 완료: payment-service → RabbitMQ topic `payment.events` → settlement-service 구독)
