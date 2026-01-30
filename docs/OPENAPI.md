# OpenAPI / Swagger UI

각 마이크로서비스는 **springdoc-openapi**로 OpenAPI 3 스펙을 노출하며, **Swagger UI**에서 브라우저로 API를 확인·테스트할 수 있습니다.

---

## 접근 방법

### 1. 로컬 (Docker Compose)

`docker compose up -d` 후 각 서비스 포트로 Swagger UI 접속:

| 서비스 | API 문서 (Swagger UI) |
|--------|----------------------|
| user-service | http://localhost:8081/api-docs.html |
| product-service | http://localhost:8082/api-docs.html |
| order-service | http://localhost:8083/api-docs.html |
| payment-service | http://localhost:8084/api-docs.html |
| settlement-service | http://localhost:8085/api-docs.html |

- **모든 서비스**: `/api-docs.html` 로 Swagger UI 접속 (CDN 기반). OpenAPI JSON은 `/v3/api-docs`.
- **API Gateway(8080)** 는 Swagger UI 없음. 위 포트에서 각 서비스 문서 확인.

---

### 2. Kubernetes (Helm 설치 후)

각 서비스 Pod에 port-forward 한 뒤 위와 동일한 경로로 접속합니다.

```bash
# 터미널을 나눠서 각각 실행 (또는 백그라운드)
kubectl port-forward svc/msa-shop-api-gateway 8080:8080
kubectl port-forward svc/msa-shop-user-service 8081:8081
kubectl port-forward svc/msa-shop-product-service 8082:8082
kubectl port-forward svc/msa-shop-order-service 8083:8083
kubectl port-forward svc/msa-shop-payment-service 8084:8084
kubectl port-forward svc/msa-shop-settlement-service 8085:8085
```

이후 브라우저에서 **각 서비스 `/api-docs.html`** 로 접속 (예: http://localhost:8081/api-docs.html).  

---

## 인증이 필요한 API (Swagger UI에서 테스트)

- **order-service**: `POST /orders`, `GET /orders/me`, `PATCH /orders/{id}/cancel` 등 → JWT 필요  
- **user-service**: `GET /users/me` → JWT 필요  

Swagger UI에서:

1. `POST /auth/login`으로 로그인 후 응답의 `accessToken` 복사  
2. Swagger UI 상단 **Authorize** 클릭  
3. `Bearer {accessToken}` 입력 (또는 `Bearer` 뒤에 공백 한 칸 두고 토큰 붙여넣기)  
4. **Authorize** 후 필요한 API 호출  

Gateway(8080) 경유로 테스트할 때는 **Server**를 `http://localhost:8080`으로 두고, 각 서비스 Swagger는 해당 서비스 포트(8081~8085)로 두면 됩니다.

---

## Swagger로 API 테스트하는 방법

### Swagger에서 테스트할 순서 (한 번에 따라 하기)

| 순서 | 어디서 | 뭘 누르는지 | 입력 예시 |
|------|--------|-------------|-----------|
| **1** | http://localhost:8081/api-docs.html (user) | **POST /users** → Try it out → Execute | `{"email":"test@test.com","password":"test1234","name":"테스트"}` → 201 확인 |
| **2** | 같은 페이지 (user) | **POST /auth/login** → Try it out → Execute | `{"email":"test@test.com","password":"test1234"}` → 응답에서 **accessToken** 복사 |
| **3** | 같은 페이지 (user) | **Authorize** 클릭 | Value에 복사한 토큰 붙여넣기 → Authorize → Close |
| **4** | 같은 페이지 (user) | **GET /users/me** → Try it out → Execute | 인증된 내 정보 조회 확인 (200) |
| **5** | http://localhost:8082/api-docs.html (product) | **GET /products** → Try it out → Execute | 상품 목록 확인 (200) |
| **6** | http://localhost:8083/api-docs.html (order) | **Authorize** 클릭 | **2번에서 복사한 같은 토큰** 붙여넣기 → Authorize → Close |
| **7** | 같은 페이지 (order) | **POST /orders** → Try it out → Execute | `{"productId":1,"quantity":2,"paymentMethod":"CARD"}` → 201, 응답에서 **id** 확인 |
| **8** | 같은 페이지 (order) | **GET /orders/me** → Try it out → Execute | 내 주문 목록 확인 (200) |
| **9** | 같은 페이지 (order) | **GET /orders/{id}** → Try it out → id에 **7번 id** 입력 → Execute | 주문 단건 조회 (200) |
| **10** | (선택) 같은 페이지 (order) | **PATCH /orders/{id}/cancel** → id에 위 주문 id → Execute | 주문 취소 (200) |

- 이미 회원이 있으면 **1번**은 건너뛰고 **2번** 로그인부터 하면 됩니다.
- **3번·6번**: user와 order Swagger는 페이지가 달라서, **order 쓸 때 Authorize에 토큰을 한 번 더** 넣어야 합니다.

---

### 1. Swagger UI 열기

서비스가 떠 있는 상태에서 브라우저로 접속:

- user: http://localhost:8081/api-docs.html  
- product: http://localhost:8082/api-docs.html  
- order: http://localhost:8083/api-docs.html  
- payment: http://localhost:8084/api-docs.html  
- settlement: http://localhost:8085/api-docs.html  

각 페이지에서 해당 서비스의 API 목록이 보이면 정상입니다.

---

### 2. 인증 없이 테스트하는 API

아래는 **로그인 없이** 바로 **Try it out** → **Execute** 로 호출할 수 있습니다.

| 서비스 | API | 설명 |
|--------|-----|------|
| user | `POST /users` | 회원가입 |
| user | `POST /auth/login` | 로그인 (JWT 발급) |
| product | `GET /products` | 상품 목록 |
| product | `GET /products/{id}` | 상품 단건 조회 |
| payment | `POST /payments` | 결제 요청 (테스트용) |
| settlement | `GET /settlements/daily` | 일별 매출 |
| settlement | `GET /settlements/monthly` | 월별 매출 |

예: **user-service** Swagger에서 **POST /auth/login** → **Try it out** → Request body에 `{"email":"test@test.com","password":"test1234"}` 입력 → **Execute**.

---

### 3. JWT가 필요한 API 테스트하기 (주문·내 정보 등)

**order-service**의 `POST /orders`, `GET /orders/me`, `PATCH /orders/{id}/cancel`, **user-service**의 `GET /users/me` 등은 **JWT**가 필요합니다.

#### Step 1: 회원가입 (이미 했으면 생략)

- http://localhost:8081/api-docs.html 접속  
- **POST /users** → **Try it out**  
- Request body 예: `{"email":"test@test.com","password":"test1234","name":"테스트"}`  
- **Execute** → 201이면 성공 (이미 있으면 409)

#### Step 2: 로그인해서 토큰 받기

- **POST /auth/login** → **Try it out**  
- Request body: `{"email":"test@test.com","password":"test1234"}`  
- **Execute**  
- 응답 본문에서 **`accessToken`** 값 전체를 복사 (긴 문자열)

#### Step 3: Swagger에 토큰 한 번만 등록 (Authorize)

- Swagger UI **상단 오른쪽**의 **Authorize** 버튼(자물쇠 아이콘) 클릭  
- **bearerAuth** 섹션의 **Value** 입력란에 **토큰만** 붙여넣기 (Bearer 는 자동으로 붙음)
  - 또는 `Bearer 복사한토큰` 형태로 입력해도 됨 (Bearer 뒤 공백 한 칸)  
- **Authorize** 클릭 → **Close**  
- 이렇게 한 번만 넣으면 해당 서비스의 인증 필요한 API 호출 시 자동으로 헤더에 포함됩니다.

#### Step 4: 인증 필요한 API 호출

- **order-service** (http://localhost:8083/api-docs.html) 로 이동  
- **Authorize** 에서 같은 토큰을 한 번 더 등록 (order-service는 별도 페이지이므로)  
- **POST /orders** → **Try it out**  
  - Request body 예: `{"productId":1,"quantity":2,"paymentMethod":"CARD"}`  
- **Execute** → 201이면 주문 생성 성공  
- **GET /orders/me** → **Execute** → 내 주문 목록 확인  
- **GET /orders/{id}** 에서 위에서 만든 주문 id로 단건 조회·취소 등 테스트

**user-service**에서 **GET /users/me** 도 같은 방식으로, **Authorize**에 토큰 넣은 뒤 **Execute** 하면 됩니다.

---

### 4. 정리

- **인증 필요 API**를 쓰려면: **user-service** Swagger에서 로그인 → `accessToken` 복사 → **Authorize**에 `Bearer {accessToken}` 입력 → 해당 서비스( user / order )에서 API 호출.  
- **order-service** Swagger를 쓸 때도 **Authorize**에 토큰을 넣어야 `POST /orders`, `GET /orders/me` 등이 401 없이 동작합니다.  
- 토큰이 만료되면 401이 나옵니다. 그때는 다시 **POST /auth/login** 으로 새 토큰을 받아 **Authorize**에 넣으면 됩니다.

---

## 트러블슈팅 (403 / 500 / Whitelabel)

- **Docker Compose 사용 시**: 코드 변경 후 **반드시 이미지 재빌드** 후 재기동해야 합니다.  
  ```bash
  docker compose build --no-cache user-service order-service settlement-service
  docker compose up -d
  ```
- **8081 (user-service) 403**: `/v3/api-docs` 접근 시 Spring Security가 차단. `SecurityConfig`에서 해당 경로를 `permitAll`로 두었으므로, 수정 반영을 위해 **user-service 이미지 재빌드** 후 재기동하세요.
- **8083 / 8085 (order, settlement) 500**: `ControllerAdviceBean` 호환 이슈(Spring 6.2 + 구 springdoc). springdoc은 **2.8.9 이상**(현재 2.8.15) 사용 시 해결됩니다. **이미지를 예전에 빌드했다면** 위처럼 해당 서비스만 재빌드하면 됩니다.
- **403 (user-service)**: 브라우저에서 **http://localhost:8081/api-docs.html** 만 사용하고, `user-service`만 재빌드 후 재기동:  
  `docker compose build --no-cache user-service && docker compose up -d user-service`
- **500 (order/settlement)**: **http://localhost:8083/api-docs.html**, **http://localhost:8085/api-docs.html** 만 사용하세요. `/swagger-ui/index.html` 은 사용하지 마세요.
- **8080 Whitelabel (404)**: API Gateway에는 Swagger UI가 없습니다. 8081~8085 각 서비스 `/api-docs.html` 만 사용하세요.
- **8082 / 8084 루트 접속 시 Whitelabel**: 루트(`/`)는 이제 `/api-docs.html` 로 리다이렉트됩니다. 직접 접속 시에는 **http://localhost:8082/api-docs.html**, **http://localhost:8084/api-docs.html** 로 열면 됩니다.
- **API 스펙 로드 실패**: 터미널에서 `curl -s http://localhost:8081/v3/api-docs | head -5` 로 200 응답인지 확인해 보세요.

---

## 참고

- 스펙 문서(마크다운): [API-SPEC.md](./API-SPEC.md)  
- springdoc 버전: `springdoc-openapi-starter-webmvc-ui:2.8.15` (WebMvc 서비스), `springdoc-openapi-starter-webflux-ui:2.8.15` (API Gateway). Spring Boot 3.5 호환.
