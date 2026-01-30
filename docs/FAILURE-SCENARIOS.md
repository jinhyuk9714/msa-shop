# 장애/실패 시나리오와 대응 전략

> 주문·결제·정산 흐름에서 발생할 수 있는 실패 케이스와 현재 구현된 대응을 정리한다.

---

## 1. 주문 생성 플로우 (`POST /orders`) 내 실패

### 1.1 재고 부족

- **발생**: order-service → product-service `POST /internal/stocks/reserve` 호출 시 재고 < 요청 수량.
- **응답**: product-service가 `{ "success": false, "reason": "재고 부족" }` 반환.
- **대응**: order-service가 `InsufficientStockException` 발생 → **409 CONFLICT** + `{ "error": "CONFLICT", "message": "재고 부족: ..." }`. 재고 예약 전이므로 별도 보상 없음.

### 1.2 결제 실패 (amount ≤ 0 등)

- **발생**: order-service → payment-service `POST /payments` 호출 시 payment-service가 `success: false` 반환(예: amount ≤ 0).
- **대응**:
  1. order-service가 **보상(SAGA)**: 이미 호출한 `POST /internal/stocks/reserve`에 대해 `POST /internal/stocks/release` 호출로 재고 복구.
  2. `PaymentFailedException` 발생 → **402 PAYMENT_REQUIRED** + `{ "error": "PAYMENT_REQUIRED", "message": "결제 실패: ..." }`.

### 1.3 결제 서비스 연결 실패 / 5xx

- **발생**: payment-service 미기동, 타임아웃, 또는 payment-service 내부 오류(5xx).
- **대응**: order-service `RestTemplate` 예외(`ResourceAccessException`, `RestClientResponseException`) → **502 BAD_GATEWAY** + `{ "error": "BAD_GATEWAY", "message": "결제 서비스 연결 실패. payment-service·RabbitMQ 기동 여부 확인." }` (또는 5xx인 경우 "결제 서비스 오류: {statusCode}").  
  결제 요청 전이므로 **재고 복구** 수행 후 예외 전파.

### 1.4 주문 저장 실패 (결제는 성공)

- **발생**: 결제 승인 후 order-service DB에 `orderRepository.save()` 실패(DB 오류 등).
- **대응 (Outbox 보상)**:
  1. order-service가 **Outbox** 테이블에 `ORDER_SAVE_FAILED` 이벤트 기록(payload: paymentId, userId, productId, quantity).
  2. **OutboxProcessor** 스케줄러(기본 5초 간격)가 PENDING 이벤트 조회 후:
     - payment-service `POST /payments/{id}/cancel` 호출(결제 취소).
     - product-service `POST /internal/stocks/release` 호출(재고 복구).
  3. 이벤트 상태를 PROCESSED로 갱신.
- **클라이언트**: 주문 저장 실패 시 서버 오류(**500**) 반환. 보상은 비동기로 수행.

### 1.5 product-service 연결 실패 / 5xx

- **발생**: product-service 미기동, 타임아웃, 5xx.
- **대응**: order-service에서 **502 BAD_GATEWAY** 반환. 재고 예약 전이면 보상 없음. 재고 예약 성공 후 결제 단계에서 예외가 나면 재고 복구 후 502.

---

## 1.6 주문 취소 (`PATCH /orders/{id}/cancel`)

### 취소 가능 조건

- **PAID** 상태만 취소 가능.
- 주문자 본인(userId 일치)만 취소 가능.
- `paymentId`가 있어야 함(신규 주문은 저장 시 포함).

### 취소 실패 케이스

- **이미 CANCELLED**: `OrderCannotBeCancelledException` → **409 CONFLICT** + `{ "error": "CONFLICT", "message": "취소할 수 없는 주문입니다. 상태: CANCELLED" }`
- **결제 정보 없음**: (기존 주문에 paymentId 미저장) → **409** + `"결제 정보가 없어 취소할 수 없습니다."`
- **주문 없음 / 본인 아님**: `OrderNotFoundException` → **404**
- **결제 취소 실패**: payment-service 404 등 → **409** + `"결제 취소 실패: ..."`

### 취소 성공 시 동작

1. payment-service `POST /payments/{id}/cancel` 호출(결제 취소)
2. product-service `POST /internal/stocks/release` 호출(재고 복구)
3. 주문 상태를 **CANCELLED**로 변경

---

## 2. 인증/인가 실패

### 2.1 JWT 없음 / 형식 오류 / 만료

- **발생**: Gateway 경유 시 `/orders/**`, `/users/me` 요청에 `Authorization: Bearer` 없거나 토큰 무효/만료.
- **대응**: API Gateway가 **401 Unauthorized** 반환. order-service / user-service 직접 호출 시에도 JWT 검증 실패하면 401.

### 2.2 사용자 없음 (GET /users/me)

- **발생**: JWT는 유효하나 DB에 해당 userId 사용자가 없음.
- **대응**: user-service **404 NOT_FOUND**.

---

## 3. 결제 완료 → 정산 (RabbitMQ) 실패

### 3.1 RabbitMQ 발행 실패 (payment-service)

- **발생**: 결제 승인 후 payment-service가 RabbitMQ로 `PaymentCompletedEvent` 발행 시 연결 끊김, 브로커 다운 등.
- **대응**: payment-service `publishPaymentCompleted()` 내부에서 **Exception catch** 후 로그만 남기고 **결제는 성공 유지**. settlement-service 쪽 매출 집계는 해당 건 누락 가능(수동 보정 또는 재발행 정책은 미구현).

### 3.2 settlement-service 미기동 / 구독자 지연

- **발생**: RabbitMQ에는 메시지 적재되나 settlement-service가 아직 기동 전이거나 일시 중단.
- **대응**: RabbitMQ Queue `settlement.payment.completed`에 메시지 유지. settlement-service 기동 후 **소비**하여 집계. 재시도/DLQ는 현재 미구현(필요 시 Dead Letter Queue 등 확장 가능).

---

## 4. 기타

### 4.1 중복 회원가입

- **발생**: `POST /users` 시 동일 이메일 이미 존재.
- **대응**: user-service **409 CONFLICT** + `{ "error": "CONFLICT", "message": "이미 존재하는 이메일입니다." }`.

### 4.2 주문/결제 단건 조회 없음

- **발생**: `GET /orders/{id}` 또는 `GET /payments/{id}/cancel` 등에서 해당 ID 없음.
- **대응**: order-service **404 NOT_FOUND** / payment-service **404**.

---

## 5. 요약 표

| 시나리오                      | HTTP   | 대응 요약                               |
| ----------------------------- | ------ | --------------------------------------- |
| 재고 부족                     | 409    | 보상 없음                               |
| 결제 실패(비즈니스)           | 402    | 재고 복구(SAGA)                         |
| payment/product 연결 실패·5xx | 502    | 재고 복구 후 502                        |
| 주문 저장 실패(결제 성공 후)  | 500    | Outbox → 스케줄러가 결제 취소·재고 복구 |
| JWT 없음/만료/오류            | 401    | -                                       |
| 결제 완료 이벤트 발행 실패    | (무시) | 로그만, 결제 성공 유지                  |
| settlement 미기동             | (지연) | Queue 적재 후 서비스 기동 시 소비       |
