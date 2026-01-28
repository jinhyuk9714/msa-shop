## 구현 현황 요약

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
  - 이미 존재하는 이메일이면 400 수준의 예외 발생(현재는 `IllegalArgumentException` 기반)

- `POST /auth/login`
  - 로그인(간단한 검증 후 더미 액세스 토큰 발급)
  - Request: `{ "email", "password" }`
  - Response: `{ "accessToken": "dummy-token-for-user-{id}" }`

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
  - 주문 단건 조회

---

## 실행 순서(예시)

1. **user-service 실행 후 회원가입/로그인**
   - `POST /users` 로 회원가입
   - `POST /auth/login` 으로 로그인 후 `accessToken` 획득  
     → 값 예시: `dummy-token-for-user-1`

2. **product-service에서 테스트용 상품 데이터 입력**  
   - 현재는 직접 DB(H2 콘솔) 또는 추가 API로 데이터 입력 필요

3. **order-service로 주문 생성**
   - 헤더: `Authorization: Bearer dummy-token-for-user-1`
   - `POST /orders`  
     Body: `{ "productId": 1, "quantity": 2, "paymentMethod": "CARD" }`

4. **payment-service는 order-service에서 내부적으로 호출**
   - 별도 직접 호출 없이 `/orders` 호출만으로 결제까지 시뮬레이션됨

---

## 앞으로의 확장 아이디어 (2단계용 메모)

- Outbox 패턴 도입 및 주문/결제 보상 트랜잭션 구현
- 결제 완료 이벤트 발행 및 `settlement-service` 추가
- MySQL 전환 및 Docker Compose로 서비스+DB 통합 실행
- user-service에 실제 JWT 발급/검증 로직 추가 및 order-service에서 JWT 파싱으로 전환

