# 로컬 실행 가이드 (1번: 기본 시나리오 태워보기)

## 사전 준비

- Java 21
- Gradle (또는 `gradle wrapper` 실행 후 `./gradlew` 사용)
- `curl`, `python3` (E2E 스크립트용)

## 1. Gradle Wrapper 준비 (최초 1회)

이미 `gradlew`·`gradle/wrapper/` 가 있다면 생략.

**Gradle이 설치된 경우** (`brew install gradle` / SDKMAN 등):

```bash
gradle wrapper
```

이후 `./gradlew` 사용. Wrapper JAR 이 없으면 `gradlew` 실행 시 안내 메시지가 뜬다.

## 2. 빌드

```bash
./gradlew build -x test
```

## 3. 네 서비스 기동

**터미널 4개**에서 각각 실행 (또는 IDE에서 Run).

| 터미널 | 명령                                 | 포트 |
| ------ | ------------------------------------ | ---- |
| 1      | `./gradlew :user-service:bootRun`    | 8081 |
| 2      | `./gradlew :product-service:bootRun` | 8082 |
| 3      | `./gradlew :payment-service:bootRun` | 8084 |
| 4      | `./gradlew :order-service:bootRun`   | 8083 |

기동 순서는 무관. **order-service**는 product / payment 에 연결하므로, 최소한 product·payment 가 먼저 떠 있는 편이 좋다.

## 4. E2E 시나리오 실행

네 서비스가 모두 떴다면:

```bash
./scripts/e2e-flow.sh
```

**재실행 시**: 이미 `test@test.com` 으로 가입했다면 2번(회원가입)에서 에러가 날 수 있다. 이때는 3번(로그인)부터 수동으로 진행하거나, 이메일을 바꿔서 회원가입하면 된다.

동작 요약:

1. **상품 목록** `GET /products` (product-service, 시딩된 테스트 상품 조회)
2. **회원가입** `POST /users` (user-service)
3. **로그인** `POST /auth/login` → `accessToken` (더미 토큰) 획득
4. **주문 생성** `POST /orders` (order-service, Bearer 토큰 + productId=1, quantity=2)
5. 성공 시 **주문 단건 조회** `GET /orders/{id}`

### 환경 변수 (기본값: localhost)

```bash
USER_URL=http://localhost:8081 PRODUCT_URL=http://localhost:8082 ORDER_URL=http://localhost:8083 ./scripts/e2e-flow.sh
```

## 5. 실패 시나리오 맛보기 (선택)

- **재고 부족**: 상품 C(id=3) 재고 5개 → quantity 10 으로 주문하면 409 수준 실패.
- **결제 실패**: payment-service 쪽 룰 확장 후 amount 등으로 실패 유도 가능 (현재는 amount ≤ 0 시 실패).

curl 예시:

```bash
# 로그인 후 TOKEN 설정
TOKEN="dummy-token-for-user-1"

# 재고 부족 (상품 3, 수량 10)
curl -s -X POST http://localhost:8083/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":3,"quantity":10,"paymentMethod":"CARD"}'
```

## 6. 트러블슈팅

- **Connection refused**: 해당 서비스 포트가 열려 있는지, `bootRun` 이 완료됐는지 확인.
- **401 / 토큰 오류**: `POST /auth/login` 응답의 `accessToken`을 그대로 `Authorization: Bearer {token}` 에 넣었는지 확인.
- **상품 없음**: product-service `ProductDataLoader` 가 테스트 상품 3종을 시딩함. H2 재시작 시 다시 들어감.
