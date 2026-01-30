# Kubernetes 배포

> 전체 스택(MySQL, RabbitMQ, 6개 앱, Ingress) 매니페스트. 적용 순서대로 배포하면 Docker Compose와 동일한 구성으로 동작합니다.  
> **Helm 사용**: 동일 스택을 Helm으로 설치하려면 [`helm/README.md`](../helm/README.md) 참고.

## 전제

- **Kubernetes 클러스터**: `kubectl apply` 전에 클러스터가 기동·연결돼 있어야 합니다. 그렇지 않으면 `failed to download openapi: the server could not find the requested resource` 오류가 납니다.
- **Docker 이미지**: yaml 기본값은 `msa-shop-*:latest`. 로컬 실행 시 아래 "로컬 이미지 빌드"대로 빌드 후 적용. 레지스트리 사용 시 이미지 이름을 `your-registry/msa-shop-*:tag` 로 바꾸면 됨.
- **Ingress Controller**: Ingress 사용 시 클러스터에 nginx ingress 등 설치 필요. Minikube: `minikube addons enable ingress`.

## 파일 구성

| 파일                           | 내용                                                |
| ------------------------------ | --------------------------------------------------- |
| `00-secret.yaml`               | Secret(DB/JWT/RabbitMQ 비밀) + ConfigMap(호스트 등) |
| `01-configmap-mysql-init.yaml` | MySQL 최초 기동 시 DB 생성 스크립트                 |
| `02-mysql.yaml`                | MySQL 8 (PVC + Deployment + Service)                |
| `03-rabbitmq.yaml`             | RabbitMQ 3-management (Deployment + Service)        |
| `10-user-service.yaml`         | user-service (8081)                                 |
| `11-product-service.yaml`      | product-service (8082)                              |
| `order-service.yaml`           | order-service (8083)                                |
| `13-payment-service.yaml`      | payment-service (8084)                              |
| `14-settlement-service.yaml`   | settlement-service (8085)                           |
| `20-api-gateway.yaml`          | api-gateway (8080)                                  |
| `30-ingress.yaml`              | Ingress: `/` → api-gateway                          |

## 적용 순서

아래 순서대로 적용해야 DB·메시징이 준비된 뒤 앱이 기동합니다.

```bash
# 1) 비밀·설정
kubectl apply -f k8s/00-secret.yaml
kubectl apply -f k8s/01-configmap-mysql-init.yaml

# 2) 인프라
kubectl apply -f k8s/02-mysql.yaml
kubectl apply -f k8s/03-rabbitmq.yaml

# MySQL Ready 대기 (선택)
kubectl wait --for=condition=ready pod -l app=mysql --timeout=120s

# 3) 앱 서비스 (의존 관계 고려해 한꺼번에 가능)
kubectl apply -f k8s/10-user-service.yaml
kubectl apply -f k8s/11-product-service.yaml
kubectl apply -f k8s/order-service.yaml
kubectl apply -f k8s/13-payment-service.yaml
kubectl apply -f k8s/14-settlement-service.yaml
kubectl apply -f k8s/20-api-gateway.yaml

# 4) Ingress (한 주소로 Gateway 경유 접속)
kubectl apply -f k8s/30-ingress.yaml
```

한 번에 적용:

```bash
kubectl apply -f k8s/
```

## 로컬 이미지로 실행 (ErrImagePull 해결)

`ErrImagePull` 은 지정한 이미지가 클러스터에서 가져올 수 없을 때 발생합니다. **Docker Desktop Kubernetes** 나 **Minikube** 에서 로컬 이미지를 쓰려면:

1. **이미지 빌드** (프로젝트 루트에서):

   ```bash
   chmod +x k8s/build-local-images.sh
   ./k8s/build-local-images.sh
   ```

   - **Minikube** 사용 시: `eval $(minikube docker-env)` 실행한 뒤 위 스크립트를 실행하면 Minikube 안의 Docker에 이미지가 만들어집니다.

2. **배포 적용** (이미 해 두었다면 앱 Pod만 재시작):

   ```bash
   kubectl apply -f k8s/
   kubectl rollout restart deployment api-gateway user-service product-service order-service payment-service settlement-service
   ```

3. **Pod 상태 확인**: `kubectl get pods` 에서 앱 Pod들이 `Running` / `1/1` 이 되면 준비 완료.

## 상태 확인

```bash
kubectl get pods
kubectl get svc
kubectl get ingress
```

## 접속 방법

- **Ingress 사용 시**: Ingress Controller가 부여한 주소(예: Minikube `minikube tunnel` 후 `http://<ingress-ip>` 또는 `kubectl get ingress` 로 확인)로 접속. Docker Desktop + nginx Ingress면 보통 `http://localhost` (80). 예: `http://localhost/orders`, `http://localhost/users`.
- **Ingress 없이**: Gateway만 노출하려면 `kubectl port-forward svc/api-gateway 8080:8080` 후 `http://localhost:8080` 으로 접속.

## E2E 검증

전체 Pod가 `1/1 Running` 이면 아래로 회원가입·로그인·주문·정산까지 한 번에 검증할 수 있다.

**1) port-forward로 Gateway 노출** (한 터미널에서 유지):

```bash
kubectl port-forward svc/api-gateway 8080:8080
```

**2) 다른 터미널에서 E2E 실행**:

```bash
GATEWAY_URL=http://localhost:8080 ./scripts/e2e-flow.sh
```

**Ingress로 80 포트 접속 가능한 경우** (예: `http://localhost`):

```bash
GATEWAY_URL=http://localhost ./scripts/e2e-flow.sh
```

성공 시 상품 목록 → 회원가입 → 로그인 → 주문 생성 → 주문 조회 → 당일 매출 집계(6단계)까지 통과.

## 트러블슈팅

- **`ErrImagePull`**  
  이미지 `msa-shop-*:latest` 가 없을 때 발생. 위 "로컬 이미지로 실행" 대로 `k8s/build-local-images.sh` 로 빌드한 뒤 적용. **Minikube** 사용 시에만 `eval $(minikube docker-env)` 실행 (Docker Desktop K8s는 생략).

- **Pod가 `0/1 Running` 이거나 재시작을 반복할 때**  
  앱이 기동 중에 DB·RabbitMQ 연결 실패 등으로 죽는 경우가 많습니다. 로그로 원인 확인:

  ```bash
  kubectl logs deployment/user-service --tail=100
  kubectl logs deployment/order-service --tail=100
  kubectl logs deployment/rabbitmq --tail=50
  ```

  MySQL·RabbitMQ가 먼저 Ready인지 확인: `kubectl get pods -l app=mysql` → `1/1 Running` 이 된 뒤 앱이 뜨도록, 필요하면 앱 Deployment를 나중에 적용하거나 `kubectl rollout restart deployment ...` 로 재시작.

- **MySQL이 CrashLoopBackOff일 때**  
  Probe timeout(기본 1초)으로 재시작될 수 있음. 매니페스트에는 `timeoutSeconds: 15` 가 들어 있으므로 `kubectl apply -f k8s/02-mysql.yaml` 로 적용. 원인 확인: `kubectl describe pod -l app=mysql` → Events에서 "probe failed" / "OOMKilled" 등 확인.

- **`failed to download openapi: the server could not find the requested resource`**  
  kubectl이 클러스터 API 서버에 연결하지 못할 때 발생합니다.
  1. 클러스터가 켜져 있는지 확인: `kubectl cluster-info`, `kubectl get nodes`
  2. **Docker Desktop**: 설정 → Kubernetes → "Enable Kubernetes" 체크 후 Apply & Restart
  3. **Minikube**: `minikube start`
  4. **컨텍스트**: `kubectl config current-context` 로 사용 중인 클러스터 확인

## 참고

- 상세 아키텍처·설정: 프로젝트 루트 `docs/ARCHITECTURE.md`, `docs/IMPLEMENTATION.md`, `docs/K8S-EXPANSION.md`.
- 로컬 실행·E2E: `docs/RUN-LOCAL.md`.
