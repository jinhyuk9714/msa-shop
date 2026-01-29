## 구현 현황 요약

### 최근 완료 작업 (1번 E2E 기준)

- Gradle 멀티 모듈: 루트 `build.gradle` Groovy 문법 정리 (`apply plugin`, `withType(Test)`), 서브모듈 동일 적용.
- Gradle Wrapper: `gradlew` + `gradle/wrapper`(properties, JAR). `java -cp … GradleWrapperMain` 실행.
- user-service: `DuplicateEmailException` + `UserControllerAdvice` → 중복 이메일 **409 CONFLICT** + JSON.
- product-service: `Product.decreaseStock`, `ProductDataLoader` 시딩, `InternalStockController` 재고 차감 정리.
- payment-service: `application.yml` (8084, H2).
- order-service: Resilience4j 2.3.0 명시, Retry/CircuitBreaker 설정.
- E2E: `scripts/e2e-flow.sh` (macOS `sed '$d'`, 409 시 회원가입 스킵 후 로그인 진행).
- 문서: `docs/RUN-LOCAL.md` 로컬 실행 가이드, `docs/IMPLEMENTATION.md` 본 문서.
- **테스트**: 필수 단위 테스트 추가. `./gradlew test` 로 실행.
- **1단계 마무리**: order-service 예외/상태코드(409 재고, 402 결제, 404 주문, 401 토큰), Resilience4j 설정, GET /users/me, GET /orders/me.

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
  - 각 서비스별 H2 메모리 DB (MySQL MODE, 추후 MySQL 전환 예정)

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
- **주요 책임**: 회원 관리, 로그인(현재는 더미 토큰 발급)

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
  - 로그인(간단한 검증 후 더미 액세스 토큰 발급)
  - Request: `{ "email", "password" }`
  - Response: `{ "accessToken": "dummy-token-for-user-{id}" }`

- `GET /users/me`
  - 내 정보 조회. `Authorization: Bearer dummy-token-for-user-{id}` 필수.
  - Response: `{ "id", "email", "name" }`. 토큰 오류 시 401, 사용자 없음 시 404.

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
  - `APPROVED`, `CANCELED` (현재는 APPROVED만 사용)

### API

- `POST /payments`
  - 결제 시도
  - Request: `{ "userId", "amount", "paymentMethod" }`
  - 간단한 룰:
    - `amount <= 0` → 실패
  - Response:
    - 성공: `{ "success": true, "paymentId": <id>, "reason": "APPROVED" }`
    - 실패: `{ "success": false, "paymentId": null, "reason": "<에러 메시지>" }`

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
   - `Authorization: Bearer dummy-token-for-user-{id}`
   - 현재는 user-service가 발급하는 **더미 토큰 규약**에 맞춰 단순 파싱

2. **상품 정보 조회**
   - `GET product-service /products/{id}`
   - 가격 정보를 가져와 `totalAmount = price * quantity` 계산

3. **재고 예약**
   - `POST product-service /internal/stocks/reserve`
   - 실패(`success=false`) 시 주문 실패로 간주하고 예외 발생

4. **결제 요청**
   - `POST payment-service /payments`
   - 실패(`success=false`) 시 주문 실패로 간주하고 예외 발생

5. **주문 저장**
   - 모든 단계가 성공하면 `Order(status = PAID)` 엔티티를 저장

### API

- `POST /orders`
  - Request 헤더:
    - `Authorization: Bearer dummy-token-for-user-{id}`
  - Body: `{ "productId", "quantity", "paymentMethod" }`
  - 성공 시:
    - 201 Created + 주문 정보 반환
  - 실패 시:
    - 현재는 `IllegalStateException` 시 `409 CONFLICT` 수준으로 메시지 반환

- `GET /orders/{id}`
  - 주문 단건 조회. 없으면 404.

- `GET /orders/me`
  - 내 주문 목록. `Authorization: Bearer dummy-token-for-user-{id}` 필수. `createdAt` 내림차순.

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
   - `POST /auth/login` 으로 로그인 후 `accessToken` 획득  
     → 값 예시: `dummy-token-for-user-1`

2. **product-service**
   - `ProductDataLoader` 로 테스트 상품 자동 등록 (별도 입력 불필요)

3. **order-service로 주문 생성**
   - 헤더: `Authorization: Bearer dummy-token-for-user-1`
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
- **order-service** `OrderServiceTest`: `createOrder` 성공(외부 HTTP Mock), 재고 예약 실패·결제 실패 시 `IllegalStateException`, `getOrder` 성공/미존재.

실행: `./gradlew test`. order-service 테스트는 `MockRestServiceServer`로 product/payment HTTP 모킹.

---

## 앞으로의 확장 아이디어 (2단계용 메모)

- Outbox 패턴 도입 및 주문/결제 보상 트랜잭션 구현
- 결제 완료 이벤트 발행 및 `settlement-service` 추가
- MySQL 전환 및 Docker Compose로 서비스+DB 통합 실행
- user-service에 실제 JWT 발급/검증 로직 추가 및 order-service에서 JWT 파싱으로 전환
