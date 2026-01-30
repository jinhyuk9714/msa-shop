# 2번: Kubernetes 확장 — 쉽고 자세한 설명

> 지금은 `order-service`만 K8s 매니페스트가 있습니다.  
> 2번에서는 **나머지 서비스 + Ingress + ConfigMap/Secret** 을 추가해서, Docker Compose처럼 **전체 스택을 K8s에서 돌릴 수 있게** 만드는 단계입니다.

---

## 1. 지금 상태 vs 목표

| 구분           | 지금                                      | 2번 완료 후                                             |
| -------------- | ----------------------------------------- | ------------------------------------------------------- |
| K8s 매니페스트 | `order-service` 하나만 있음               | 6개 앱 + MySQL + RabbitMQ + Ingress                     |
| 외부 접속      | 각 서비스마다 `kubectl port-forward` 필요 | **한 주소**(예: `http://localhost`)로 Gateway 경유 접속 |
| 설정/비밀      | yaml에 직접 적혀 있음                     | ConfigMap·Secret으로 분리                               |

---

## 2. K8s에서 쓰는 것들 (최소한만)

### 2.1 Pod

- **뭔가**: 컨테이너(우리 앱)가 실제로 돌아가는 **한 덩어리**.
- **역할**: 예를 들어 "order-service 컨테이너 1개"가 한 Pod 안에서 실행됨.

### 2.2 Deployment

- **뭔가**: "이 이미지로 Pod를 이만큼 유지해 줘"라고 K8s에 알려주는 **설명서**.
- **역할**:
  - **몇 개** Pod를 띄울지 (`replicas: 1` 등)
  - **어떤 이미지**를 쓸지 (`image: ...`)
  - **환경 변수** (DB URL, JWT 시크릿 등)
  - **헬스체크** (readiness/liveness) → 장애 시 자동 재시작·로드밸런싱 제외
- **비유**: Docker Compose의 `order-service` 서비스 하나를 K8s 방식으로 옮긴 것.

### 2.3 Service (ClusterIP)

- **뭔가**: Pod들 앞에 붙는 **고정 이름(주소)**.
- **역할**:
  - 클러스터 **안**에서만 쓰는 주소 예: `http://order-service:8083`.
  - Pod가 여러 개여도 이 이름 하나로 접근하면 K8s가 알아서 나눠 줌.
- **비유**: Docker Compose에서 `order-service`라는 서비스 이름으로 다른 컨테이너가 접속하는 것과 같음.

### 2.4 Ingress

- **뭔가**: 클러스터 **밖**에서 들어오는 요청을 **경로별로** 어떤 Service로 넘길지 정해 주는 **문지기**.
- **역할**:
  - 예: `http://localhost/orders/*` → `api-gateway` Service로 전달.
  - 실제로는 보통 `http://localhost` 하나만 열고, Gateway가 다시 `/users`, `/products`, `/orders` 등으로 나눔.
- **비유**: Docker Compose에서 `ports: "8080:8080"` 으로 **한 포트(8080)** 만 열고, 그 뒤에 Gateway가 있는 것과 비슷.

### 2.5 ConfigMap / Secret

- **ConfigMap**: **일반 설정값** (DB 호스트명, 서비스 base URL 등). 노출돼도 큰 문제 없는 것.
- **Secret**: **비밀값** (DB 비밀번호, JWT 시크릿). base64 등으로 저장하고, Pod에서 환경 변수로 주입.
- **역할**: yaml에 비밀/설정을 직접 안 쓰고 **리소스로 분리** → 환경마다 다른 값 넣기 쉽고, 보안·관리 편해짐.

---

## 3. 2번에서 할 일 (순서대로)

### 3.1 인프라 리소스 (MySQL, RabbitMQ)

- **MySQL**: Deployment 1개 + Service 1개.
  - 기존 `docker/mysql/init` 스크립트로 DB 생성하려면 Init Container 또는 별도 Job을 둘 수 있음.
  - 간단히 하려면 **PersistentVolumeClaim + 단일 MySQL Pod** 로 한 번에 띄우고, 초기 스키마는 이미지/스크립트로 처리.
- **RabbitMQ**: Deployment 1개 + Service 1개.
  - payment / settlement 가 `rabbitmq:5672` 로 접속하도록 Service 이름만 맞추면 됨.

### 3.2 앱 서비스 6개 (Deployment + Service)

- **이미 있는 것**: `order-service` (Deployment + Service).
- **추가할 것**:
  - **api-gateway** (8080)
    - env: `USER_SERVICE_URI`, `PRODUCT_SERVICE_URI`, `ORDER_SERVICE_URI`, `APP_JWT_SECRET`, Zipkin 등.
  - **user-service** (8081)
    - env: DB URL/계정, `APP_JWT_SECRET`.
  - **product-service** (8082)
    - env: DB URL/계정, Zipkin(선택).
  - **payment-service** (8084)
    - env: DB, RabbitMQ 호스트/포트/계정, Zipkin(선택).
  - **settlement-service** (8085)
    - env: DB, RabbitMQ.

**작업 방식**:  
`k8s/order-service.yaml` 을 **복사해서**

- 이름만 바꾸고 (예: `user-service`)
- 포트·이미지·환경 변수만 **docker-compose** 에 맞게 바꾸면 됨.  
  각 서비스별로 `k8s/user-service.yaml`, `k8s/product-service.yaml` … 처럼 파일을 나누어 두면 관리하기 쉽습니다.

### 3.3 Ingress (한 주소로 들어오게)

- **한 개** Ingress 리소스를 만듦.
- 규칙 예시:
  - 경로: `/` (또는 `/*`) → **api-gateway** Service (port 8080).
- 이렇게 하면:
  - `kubectl port-forward svc/ingress-controller 80:80` (또는 로드밸런서 80) 한 번만 열면
  - 브라우저에서 `http://localhost/orders`, `http://localhost/users` … 처럼 **Gateway 경유**로 모든 API 사용 가능.

> Ingress를 쓰려면 클러스터에 **Ingress Controller**(예: nginx-ingress, ingress-nginx)가 설치돼 있어야 합니다. Minikube는 보통 `minikube addons enable ingress` 로 활성화.

### 3.4 ConfigMap / Secret (설정·비밀 분리)

- **Secret 1개** (예: `msa-shop-secrets`):
  - `mysql-password`, `jwt-secret`, `rabbitmq-password` 등.
  - 각 Deployment에서 `env.valueFrom.secretKeyRef` 로 참조.
- **ConfigMap 1개** (예: `msa-shop-config`):
  - DB 호스트/포트, RabbitMQ 호스트, 서비스 base URL 등.
  - `env.valueFrom.configMapKeyRef` 로 참조.

이렇게 하면:

- yaml에 비밀을 안 씀.
- 로컬/스테이징/운영만 ConfigMap·Secret 값만 바꿔서 같은 매니페스트 재사용 가능.

---

## 4. 요청이 들어오면 (흐름)

1. 사용자: `http://localhost/orders` 요청
2. **Ingress**: "경로가 /orders 로 시작하네 → api-gateway Service로 전달"
3. **api-gateway (Pod)**: JWT 검증, `X-User-Id` 붙여서 `http://order-service:8083` 로 전달
4. **order-service (Pod)**: 주문 처리, 필요 시 `http://product-service:8082`, `http://payment-service:8084` 호출
5. 응답이 같은 경로로 돌아감

Docker Compose에서 `8080` 하나 열고 Gateway 쓰는 것과 **같은 흐름**입니다. 단지 플랫폼이 K8s일 뿐입니다.

---

## 5. 정리 (체크리스트)

| 단계 | 할 일                                           | 설명                                                              |
| ---- | ----------------------------------------------- | ----------------------------------------------------------------- |
| ①    | MySQL Deployment + Service                      | orderdb, userdb 등 DB 생성 (init 스크립트 또는 수동)              |
| ②    | RabbitMQ Deployment + Service                   | payment / settlement 가 5672로 접속                               |
| ③    | user, product, payment, settlement, api-gateway | 각각 Deployment + Service (order-service 복제 후 이름·env만 수정) |
| ④    | Ingress 1개                                     | `/` → api-gateway:8080                                            |
| ⑤    | ConfigMap / Secret                              | DB·JWT·RabbitMQ 값 분리 후 env에서 참조                           |

이 순서대로 하면 **Docker Compose와 같은 구성**을 K8s에서 그대로 재현할 수 있고,  
나중에 HPA(자동 스케일), 롤링 업데이트, 다른 클라우드 연동으로 넘어가기도 쉽습니다.
