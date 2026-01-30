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

| 터미널 | 명령                                                                        | 포트                     |
| ------ | --------------------------------------------------------------------------- | ------------------------ |
| 1      | `./gradlew :user-service:bootRun`                                           | 8081                     |
| 2      | `./gradlew :product-service:bootRun`                                        | 8082                     |
| 3      | `./gradlew :payment-service:bootRun`                                        | 8084                     |
| 4      | `./gradlew :order-service:bootRun`                                          | 8083                     |
| 5      | `./gradlew :settlement-service:bootRun`                                     | 8085 (선택, 매출 집계용) |
| (선택) | RabbitMQ: `docker run -d -p 5672:5672 -p 15672:15672 rabbitmq:3-management` | 5672, 15672              |

기동 순서는 무관. **order-service**는 product / payment 에 연결. **payment-service**는 결제 완료 시 **RabbitMQ**로 이벤트 발행. settlement-service를 쓸 경우 RabbitMQ를 먼저 띄우고 payment·settlement를 기동해야 한다. RabbitMQ 미기동 시 발행 실패 로그만 남고 결제는 성공 처리.

## 4. E2E 시나리오 실행

네 서비스가 모두 떴다면:

```bash
./scripts/e2e-flow.sh
```

**재실행 시**: 이미 `test@test.com` 으로 가입했다면 2번(회원가입)에서 409가 날 수 있다. 이때는 3번(로그인)부터 진행하면 된다.  
**BCrypt 전환 후**: 예전에 평문 비밀번호로 가입한 계정은 로그인이 안 된다. 아래 "BCrypt 전환 후 기존 계정" 참고.

동작 요약:

1. **상품 목록** `GET /products` (product-service, 시딩된 테스트 상품 조회)
2. **회원가입** `POST /users` (user-service)
3. **로그인** `POST /auth/login` → `accessToken` (JWT) 획득
4. **주문 생성** `POST /orders` (order-service, Bearer JWT + productId=1, quantity=2)
5. 성공 시 **주문 단건 조회** `GET /orders/{id}`

**API 문서 (OpenAPI/Swagger)**: 각 서비스 기동 후 브라우저에서 Swagger UI로 확인. 상세·테스트 순서는 [docs/OPENAPI.md](OPENAPI.md) 참고.

| 서비스             | Swagger UI (api-docs.html)       | OpenAPI 스펙        |
| ------------------ | --------------------------------- | ------------------- |
| user-service       | http://localhost:8081/api-docs.html | http://localhost:8081/v3/api-docs |
| product-service    | http://localhost:8082/api-docs.html | http://localhost:8082/v3/api-docs |
| order-service      | http://localhost:8083/api-docs.html | http://localhost:8083/v3/api-docs |
| payment-service    | http://localhost:8084/api-docs.html | http://localhost:8084/v3/api-docs |
| settlement-service | http://localhost:8085/api-docs.html | http://localhost:8085/v3/api-docs |

Docker Compose 사용 시 위 포트는 호스트에서 동일하게 접근 가능.

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
TOKEN="<로그인 응답의 accessToken(JWT)을 여기에 넣기>"

# 재고 부족 (상품 3, 수량 10)
curl -s -X POST http://localhost:8083/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":3,"quantity":10,"paymentMethod":"CARD"}'
```

**전체 실패 시나리오 스크립트** (재고 부족 409, 주문 취소 성공·재취소 409):

```bash
./scripts/e2e-failure-scenarios.sh
# Gateway 경유: GATEWAY_URL=http://localhost:8080 ./scripts/e2e-failure-scenarios.sh
```

## 5-1. E2E 시나리오 전체 목록

| 스크립트 | 내용 |
|----------|------|
| `e2e-flow.sh` | 기본 흐름: 상품 목록 → 회원가입 → 로그인 → 주문 생성 → 주문 조회 → 당일 정산 |
| `e2e-auth-scenarios.sh` | 인증: 회원가입 중복 409, 로그인 실패 401, JWT 없음 401, 유효 JWT 200 |
| `e2e-order-scenarios.sh` | 주문: 생성 201 → 단건 조회 200 → 내 주문 목록 200 |
| `e2e-failure-scenarios.sh` | 실패: 재고 부족 409, 주문 취소 성공, 이미 취소 재취소 409 |
| `e2e-not-found-scenarios.sh` | 404: 없는 주문 조회 404, 없는 상품 조회 (404/500) |
| `e2e-product-search.sh` | 상품 검색: name, minPrice, maxPrice, 빈 결과 0건 |
| `e2e-settlement-scenarios.sh` | 정산: 일별/월별 목록, 특정 일자·월, 잘못된 형식 |
| `e2e-rate-limit.sh` | Rate Limit: 130회 요청 → 429 발생 (Gateway만, GATEWAY_URL 필요) |
| **e2e-all-scenarios.sh** | 위 1~7(+8) 시나리오 일괄 실행 |

**일괄 실행** (사전에 `e2e-flow.sh` 한 번 실행 권장 — 회원 생성):

```bash
# Gateway 경유 (K8s 시 port-forward 후)
GATEWAY_URL=http://localhost:8080 ./scripts/e2e-all-scenarios.sh

# 직접 호출
./scripts/e2e-all-scenarios.sh
```

**429(Too Many Requests) 방지**: Gateway 경유로 일괄 E2E를 돌릴 때 Rate Limit에 걸리지 않으려면, api-gateway 기동 시 아래 중 하나를 적용하세요.

- **환경 변수**: `RATE_LIMIT_PER_MINUTE=0` (0 = 비활성화)
- **프로필**: `SPRING_PROFILES_ACTIVE=e2e` (application-e2e.yml에서 per-minute: 0 적용)

예: `RATE_LIMIT_PER_MINUTE=0 ./gradlew :api-gateway:bootRun` 후 `GATEWAY_URL=http://localhost:8080 ./scripts/e2e-all-scenarios.sh` 실행.

## 5-2. 상품 검색·Rate Limit E2E (선택)

**상품 검색** (`GET /products?name=...&minPrice=...&maxPrice=...`):

```bash
# product-service 직접 (8082) 또는 Gateway(8080)
./scripts/e2e-product-search.sh
# GATEWAY_URL=http://localhost:8080 ./scripts/e2e-product-search.sh
```

**API Gateway Rate Limit** (IP당 분당 120회 초과 시 429). Gateway가 떠 있어야 함:

```bash
# 130회 연속 요청 → 제한(120) 초과 시 429 확인
GATEWAY_URL=http://localhost:8080 ./scripts/e2e-rate-limit.sh
# 빠른 확인: api-gateway에 RATE_LIMIT_PER_MINUTE=10 설정 후
# RATE_LIMIT_TEST_REQUESTS=15 GATEWAY_URL=http://localhost:8080 ./scripts/e2e-rate-limit.sh
```

## 6. Docker Compose로 API Gateway + 5서비스 + MySQL 기동 (선택)

Docker·Docker Compose가 설치돼 있다면, **MySQL 8**과 다섯 서비스를 한 번에 띄울 수 있다.

```bash
docker-compose up --build -d
```

- **API Gateway**: 포트 **8080**. 클라이언트 단일 진입점. `/users/**`, `/auth/**` → user-service, `/products/**` → product-service, `/orders/**` → order-service. JWT 검증 후 `X-User-Id` 헤더로 downstream 전달.
- **MySQL**: 포트 3306. 최초 기동 시 `docker/mysql/init/01-create-databases.sql`로 5개 DB 생성.
- **RabbitMQ**: 포트 5672(AMQP), 15672(관리 UI). 결제 완료 이벤트는 payment-service → RabbitMQ → settlement-service로 전달.
- **관측성**: **Prometheus** 9090(메트릭 수집), **Grafana** 3000(대시보드, 로그인 admin/admin), **Zipkin** 9411(분산 추적 UI). 각 서비스 `/actuator/prometheus` 스크래핑. 주문 플로우(api-gateway → order → product/payment) 트레이스는 Zipkin에서 조회.
- **호스트 포트**: 8080(gateway), 8081~8085(서비스), 3306(mysql), 5672·15672(rabbitmq), **9090(prometheus), 3000(grafana), 9411(zipkin)**.

기동 후 E2E (Gateway 경유, 권장):

```bash
GATEWAY_URL=http://localhost:8080 ./scripts/e2e-flow.sh
```

직접 서비스 호출(기존 방식):

```bash
./scripts/e2e-flow.sh
```

order-service는 `PRODUCT_SERVICE_BASE_URL` / `PAYMENT_SERVICE_BASE_URL` 로 product·payment 컨테이너에 연결한다. 로컬에서만 서비스 띄울 때는 기본값 **H2 메모리 DB**를 사용한다.

종료:

```bash
docker-compose down
```

## 7. Kubernetes에서 E2E (선택)

K8s에 전체 스택을 배포한 뒤, Gateway를 port-forward 하거나 Ingress로 노출하고 E2E를 돌릴 수 있다. 상세는 [`k8s/README.md`](../k8s/README.md) 참고.

**요약**

1. `kubectl apply -f k8s/` 및 로컬 이미지 빌드(`./k8s/build-local-images.sh`) 후 Pod가 모두 `1/1 Running` 인지 확인.
2. Gateway 노출: `kubectl port-forward svc/api-gateway 8080:8080` (백그라운드 또는 별도 터미널 유지).
3. E2E 실행: `GATEWAY_URL=http://localhost:8080 ./scripts/e2e-flow.sh`
4. Ingress로 80 포트 접속 가능하면: `GATEWAY_URL=http://localhost ./scripts/e2e-flow.sh`

## 8. 트러블슈팅

- **Connection refused**: 해당 서비스 포트가 열려 있는지, `bootRun` 이 완료됐는지 확인.
- **401 / 토큰 오류**: `POST /auth/login` 응답의 `accessToken`(JWT)을 그대로 `Authorization: Bearer {token}` 에 넣었는지 확인. 만료된 JWT면 재로그인.
- **상품 없음**: product-service `ProductDataLoader` 가 테스트 상품 3종을 시딩함. H2 재시작 시 다시 들어감.
- **Docker MySQL 연결 실패**: MySQL 컨테이너가 healthy 된 뒤 서비스가 기동하므로, `docker-compose up` 후 잠시 기다렸다가 E2E 실행.

### BCrypt 전환 후 기존 계정 로그인 안 될 때

비밀번호를 BCrypt로 저장하도록 바꾼 뒤에는, **그 전에 평문으로 저장된 계정**은 로그인할 수 없습니다. 아래 둘 중 하나로 맞추면 됩니다.

**방법 1: 새로 회원가입해서 E2E 돌리기**

- **다른 이메일로 가입**: E2E 스크립트는 `test@test.com` 을 쓰므로, 스크립트를 수정해 새 이메일(예: `test2@test.com`)을 쓰거나,
- **기존 DB 유지한 채** `test@test.com` 은 쓰지 않고, **회원가입(2번)부터 새 이메일로 수동**으로 한 뒤 3번(로그인)~5번을 진행합니다.
- 또는 **방법 2**처럼 user DB만 비운 뒤 E2E를 그대로 실행하면, 2번에서 `test@test.com` 이 새로 가입되고 BCrypt로 저장되어 3번 로그인이 됩니다.

**방법 2: DB 비우고 E2E 처음부터 돌리기**

- **Docker Compose 사용 중이면**
  1. `docker-compose down -v` 로 컨테이너와 **볼륨까지 삭제** (MySQL 데이터 초기화).
  2. `docker-compose up -d` 로 다시 기동 (MySQL 초기화 스크립트로 DB 재생성).
  3. `./scripts/e2e-flow.sh` 실행 → 회원가입(2번)에서 `test@test.com` 이 새로 가입되고, 비밀번호가 BCrypt로 저장되어 로그인·주문까지 진행됩니다.
- **로컬에서 H2로 서비스만 띄운 경우**
  - user-service를 **한 번 종료했다가 다시** `./gradlew :user-service:bootRun` 으로 기동하면 H2 메모리 DB가 비워집니다.
  - 그 다음 `./scripts/e2e-flow.sh` 를 실행하면 2번에서 새로 가입·BCrypt 저장 후 3번 로그인이 됩니다.
