# REST API 스펙 요약

> 클라이언트는 **API Gateway(8080)** 단일 진입점 사용. Gateway 경유 시 `/users`, `/auth`, `/products`, `/orders` 는 동일 경로로 라우팅되며, `/orders/**`, `/users/me` 는 **Authorization: Bearer &lt;JWT&gt;** 필수.

---

## API Gateway (8080)

| 경로           | 대상 서비스          | 인증                                   |
| -------------- | -------------------- | -------------------------------------- |
| `/users/**`    | user-service:8081    | `/users`, `/auth/login` 제외 permitAll |
| `/auth/**`     | user-service:8081    | permitAll                              |
| `/products/**` | product-service:8082 | permitAll                              |
| `/orders/**`   | order-service:8083   | JWT 검증 후 X-User-Id 전달             |
| `/cart/**`     | order-service:8083   | JWT 검증 후 X-User-Id 전달             |

- JWT 검증 실패/없음 → **401 Unauthorized**
- 인증 필요 경로: `POST/GET /orders/**`, `GET /cart/**`, `GET /users/me`

---

## user-service (8081)

| 메서드 | 경로          | 설명             | 인증       |
| ------ | ------------- | ---------------- | ---------- |
| POST   | `/users`      | 회원가입         | X          |
| POST   | `/auth/login` | 로그인(JWT 발급) | X          |
| GET    | `/users/me`   | 내 정보 조회     | Bearer JWT |

### 요청/응답

- **POST /users**  
  Request: `{ "email": "string", "password": "string", "name": "string" }`  
  Response 201: `{ "id": number, "email": "string", "name": "string" }`  
  Response 409: `{ "error": "CONFLICT", "message": "이미 존재하는 이메일입니다." }`

- **POST /auth/login**  
  Request: `{ "email": "string", "password": "string" }`  
  Response 200: `{ "accessToken": "JWT string" }`

- **GET /users/me**  
  Response 200: `{ "id": number, "email": "string", "name": "string" }`  
  Response 401: 토큰 없음/만료  
  Response 404: 사용자 없음

---

## product-service (8082)

| 메서드 | 경로                       | 설명            | 인증 |
| ------ | -------------------------- | --------------- | ---- |
| GET    | `/products`                | 상품 목록·검색  | X    |
| GET    | `/products/{id}`           | 상품 상세       | X    |
| POST   | `/internal/stocks/reserve` | 재고 예약(내부) | X    |
| POST   | `/internal/stocks/release` | 재고 복구(보상) | X    |

### 요청/응답

- **GET /products**  
  Query(선택): `name`(상품명 부분 일치), `category`(카테고리 일치), `minPrice`, `maxPrice`(가격 범위). 없으면 전체 목록.  
  Response 200: `[{ "id", "name", "category", "price", "stockQuantity" }, ...]`

- **GET /products/{id}**  
  Response 200: `{ "id", "name", "category", "price", "stockQuantity" }`

- **POST /internal/stocks/reserve**  
  Request: `{ "userId": number, "productId": number, "quantity": number }`  
  Response 200: `{ "success": boolean, "reason": "string", "remainingStock": number }`

- **POST /internal/stocks/release**  
  Request: `{ "userId", "productId", "quantity" }`  
  Response 200: (성공 여부만 사용)

---

## order-service (8083)

| 메서드 | 경로               | 설명           | 인증                      |
| ------ | ------------------ | -------------- | ------------------------- |
| POST   | `/orders`          | 주문 생성      | Bearer JWT 또는 X-User-Id |
| POST   | `/orders/from-cart`| 장바구니 전체 주문 | Bearer JWT 또는 X-User-Id |
| GET    | `/orders/{id}`     | 주문 단건 조회 | Bearer JWT 또는 X-User-Id |
| GET    | `/orders/me`       | 내 주문 목록   | Bearer JWT 또는 X-User-Id |
| PATCH  | `/orders/{id}/cancel` | 주문 취소  | Bearer JWT 또는 X-User-Id |
| GET    | `/cart`            | 장바구니 조회  | Bearer JWT 또는 X-User-Id |
| POST   | `/cart/items`      | 장바구니 추가  | Bearer JWT 또는 X-User-Id |
| PATCH  | `/cart/items/{productId}` | 수량 변경 | Bearer JWT 또는 X-User-Id |
| DELETE | `/cart/items/{productId}` | 항목 삭제 | Bearer JWT 또는 X-User-Id |
| DELETE | `/cart`            | 장바구니 비우기 | Bearer JWT 또는 X-User-Id |

### 요청/응답

- **POST /orders**  
  Headers: `Authorization: Bearer <JWT>` (또는 Gateway가 X-User-Id 전달)  
  Request: `{ "productId": number, "quantity": number, "paymentMethod": "string" }`  
  Response 201: `{ "id", "userId", "productId", "quantity", "totalAmount", "status": "PAID" }`  
  Response 409: 재고 부족 `{ "error": "CONFLICT", "message": "..." }`  
  Response 402: 결제 실패 `{ "error": "PAYMENT_REQUIRED", "message": "..." }`  
  Response 502: payment/product 연결 실패 `{ "error": "BAD_GATEWAY", "message": "..." }`

- **POST /orders/from-cart**  
  장바구니에 담긴 품목별로 주문 생성 후 장바구니 비움.  
  Request(선택): `{ "paymentMethod": "string" }` (생략 시 CARD)  
  Response 201: `[{ "id", "userId", "productId", "quantity", "totalAmount", "status": "PAID" }, ...]`  
  Response 400: 장바구니 비어 있음 `{ "error": "BAD_REQUEST", "message": "장바구니가 비어 있습니다." }`  
  Response 409/402/502: 품목 중 재고 부족·결제 실패·연결 실패(실패한 품목까지 생성된 주문은 유지, 장바구니는 비우지 않음)

- **GET /orders/{id}**  
  Response 200: `{ "id", "userId", "productId", "quantity", "totalAmount", "status" }`  
  Response 404: `{ "error": "NOT_FOUND", "message": "..." }`  
  Response 401: 토큰 없음/오류

- **GET /orders/me**  
  Response 200: `[{ "id", "userId", "productId", "quantity", "totalAmount", "status", "createdAt" }, ...]`

- **PATCH /orders/{id}/cancel**  
  PAID 상태 주문만 취소 가능. 결제 취소 + 재고 복구 후 status=CANCELLED.  
  Response 200: `{ "id", "userId", "productId", "quantity", "totalAmount", "status": "CANCELLED" }`  
  Response 409: 이미 취소됨/결제 정보 없음 `{ "error": "CONFLICT", "message": "..." }`  
  Response 404: 주문 없음 또는 본인 주문 아님

### 장바구니

- **GET /cart**  
  Response 200: `[{ "productId", "quantity" }, ...]`  
  Response 401: 토큰 없음/오류

- **POST /cart/items**  
  Request: `{ "productId": number, "quantity": number }`  
  Response 201: `{ "productId", "quantity" }` (동일 상품이 있으면 수량 합산)  
  Response 409: 재고 부족 `{ "error": "CONFLICT", "message": "..." }`  
  Response 401: 토큰 없음/오류

- **PATCH /cart/items/{productId}**  
  Request: `{ "quantity": number }` (0 이하면 삭제)  
  Response 200: `{ "productId", "quantity" }`  
  Response 204: quantity 0으로 삭제된 경우  
  Response 404: 해당 상품이 장바구니에 없음  
  Response 409: 재고 부족

- **DELETE /cart/items/{productId}**  
  Response 204: 삭제 완료

- **DELETE /cart**  
  Response 204: 장바구니 비우기 완료

---

## payment-service (8084, 내부 호출)

| 메서드 | 경로                    | 설명            |
| ------ | ----------------------- | --------------- |
| POST   | `/payments`             | 결제 시도       |
| POST   | `/payments/{id}/cancel` | 결제 취소(보상) |

### 요청/응답

- **POST /payments**  
  Request: `{ "userId": number, "amount": number, "paymentMethod": "string" }`  
  Response 200: `{ "success": true, "paymentId": number, "reason": "APPROVED" }`  
  Response 400: `{ "success": false, "paymentId": null, "reason": "string" }` (amount ≤ 0 등)

- **POST /payments/{id}/cancel**  
  Response 200: 취소 완료  
  Response 404: 해당 결제 없음

---

## settlement-service (8085)

| 메서드 | 경로                   | 설명                                |
| ------ | ---------------------- | ----------------------------------- |
| GET    | `/settlements/daily`   | 일별 매출 집계(목록 또는 특정 일자) |
| GET    | `/settlements/monthly` | 월별 매출 집계(목록 또는 특정 월)   |

### 요청/응답

- **GET /settlements/daily**  
  Query: `date`(선택, yyyy-MM-dd). 없으면 최근 일별 목록(최대 30).  
  Response 200(목록): `[{ "settlementDate", "totalAmount", "paymentCount" }, ...]`  
  Response 200(단건): `{ "settlementDate", "totalAmount", "paymentCount" }`  
  Response 404: 해당 일자 없음

- **GET /settlements/monthly**  
  Query: `yearMonth`(선택, yyyy-MM). 없으면 최근 월별 목록(최대 12).  
  Response 200(목록): `[{ "yearMonth", "totalAmount", "paymentCount" }, ...]`  
  Response 200(단건): `{ "yearMonth", "totalAmount", "paymentCount" }`  
  Response 404: 해당 월 없음

- **결제 완료 이벤트**: RabbitMQ Queue `settlement.payment.completed` 구독. HTTP API 없음.

---

## 공통 에러 응답 형식

- `{ "error": "코드", "message": "사람이 읽을 메시지" }`
- Gateway JWT 실패: 401 Body 없거나 JSON `error`/`message`
