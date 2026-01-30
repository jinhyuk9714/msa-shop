# Helm 차트 (msa-shop)

K8s 매니페스트(`k8s/`)를 Helm 차트로 패키지한 버전입니다.  
동일 스택(MySQL, RabbitMQ, 6개 앱, Ingress)을 **한 번에 설치·업그레이드·삭제**할 수 있고, **다중 설치**(여러 릴리스 이름)를 지원합니다.

## 전제

- **Helm 3** 설치: [Helm 설치 가이드](https://helm.sh/docs/intro/install/)
- Kubernetes 클러스터·Ingress Controller·이미지 준비는 `k8s/README.md`와 동일

**기존에 `kubectl apply -f k8s/`로 배포한 적이 있으면** Secret·ConfigMap·Deployment 등 이름이 겹칩니다. Helm 설치 전에 기존 리소스를 삭제하세요 (아래 트러블슈팅).

## 설치

**방법 A) ghcr.io 이미지 사용** (GitHub Actions로 main 푸시 시 자동 빌드된 이미지):

```bash
# values-ghcr.yaml 의 owner를 본인 GitHub 사용자명으로 수정 후
helm install msa-shop ./helm/msa-shop -f helm/msa-shop/values-ghcr.yaml
```

**방법 B) 로컬 이미지 사용**:

```bash
# 먼저 빌드 (Minikube: eval $(minikube docker-env) 후 실행)
./k8s/build-local-images.sh

# 기본값으로 설치 (릴리스 이름: msa-shop)
helm install msa-shop ./helm/msa-shop

# 다른 네임스페이스에 설치
helm install msa-shop ./helm/msa-shop -n shop --create-namespace
```

## 설치 후 할 일

**1) Pod 상태 확인**  
MySQL·RabbitMQ가 먼저 뜨고, 그다음 6개 앱이 기동합니다. 전부 `1/1 Running`이 될 때까지 기다리세요.

```bash
kubectl get pods
# READY가 1/1, STATUS가 Running이면 준비 완료 (1~2분 걸릴 수 있음)
```

**2) API Gateway로 접속**  
두 가지 중 하나로 접속할 수 있습니다.

- **port-forward** (로컬에서 바로 테스트할 때):
  ```bash
  kubectl port-forward svc/msa-shop-api-gateway 8080:8080
  ```
  브라우저/curl: `http://localhost:8080` (다른 터미널에서)

- **Ingress 사용 시**: 클러스터에 Ingress Controller가 있으면 `kubectl get ingress`로 주소 확인 후 해당 URL로 접속 (예: `http://localhost` 또는 Minikube tunnel 주소).

**3) E2E로 동작 검증**  
port-forward를 켠 상태에서 **다른 터미널**에서:

```bash
GATEWAY_URL=http://localhost:8080 ./scripts/e2e-flow.sh
```

Gateway만 port-forward 하면 **1~6단계**(상품·회원가입·로그인·주문·주문 조회·당일 매출 집계) 모두 통과합니다. `/settlements/**` 는 API Gateway를 통해 settlement-service로 라우팅됩니다.

**4) 관측성 (Prometheus, Grafana, Zipkin)**  
기본값으로 함께 배포됩니다. port-forward로 접속:

```bash
# Prometheus (메트릭 조회·쿼리)
kubectl port-forward svc/msa-shop-prometheus 9090:9090
# 브라우저: http://localhost:9090

# Grafana (대시보드, admin/admin)
kubectl port-forward svc/msa-shop-grafana 3000:3000
# 브라우저: http://localhost:3000 → Prometheus 데이터소스 이미 등록됨

# Zipkin (분산 추적)
kubectl port-forward svc/msa-shop-zipkin 9411:9411
# 브라우저: http://localhost:9411
```

비활성화: `helm upgrade msa-shop ./helm/msa-shop --set observability.enabled=false`

**5) 필요 시 로그·재시작**  
문제가 있으면:

```bash
kubectl logs deployment/msa-shop-user-service --tail=50
kubectl rollout restart deployment/msa-shop-api-gateway
```

## 리소스·HPA

- **Pod 리소스**: 모든 앱 Deployment에 `resources.requests`/`limits`가 적용됩니다. 기본값은 `values.yaml`의 `defaultResources`(예: requests 256Mi/100m, limits 512Mi/500m). 서비스별 오버라이드:
  ```bash
  --set orderService.resources.requests.memory=512Mi \
  --set orderService.resources.limits.cpu=1000m
  ```
- **order-service HPA**: CPU 사용률 기반 자동 스케일. 기본값 `minReplicas=1`, `maxReplicas=3`, `targetCPUUtilizationPercentage=70`. **클러스터에 metrics-server가 있어야** 동작합니다.
  - 비활성화: `--set orderService.hpa.enabled=false`
  - 조정: `--set orderService.hpa.maxReplicas=5 --set orderService.hpa.targetCPUUtilizationPercentage=80`

## 값 오버라이드

- **이미지 레지스트리/태그**: `--set userService.image.repository=myreg/msa-shop-user-service` 등
- **비밀값**: `--set secrets.mysqlRootPassword=xxx` (운영에서는 비밀 관리 도구 사용 권장)
- **Ingress 호스트**: `--set ingress.host=shop.example.com`
- **Ingress 비활성화**: `--set ingress.enabled=false`
- **관측성 비활성화**: `--set observability.enabled=false`

예:

```bash
helm install msa-shop ./helm/msa-shop \
  --set ingress.host=msa-shop.local \
  -f helm/msa-shop/values.yaml
```

## 업그레이드·삭제

```bash
# 로컬 이미지
helm upgrade msa-shop ./helm/msa-shop

# ghcr.io 이미지 (CI로 새 이미지 푸시된 후)
helm upgrade msa-shop ./helm/msa-shop -f helm/msa-shop/values-ghcr.yaml
helm uninstall msa-shop
```

## 차트 구조

| 경로 | 설명 |
|------|------|
| `msa-shop/Chart.yaml` | 차트 메타데이터 |
| `msa-shop/values.yaml` | 기본 값(이미지, 레플리카, 비밀, Ingress, observability 등) |
| `msa-shop/templates/` | Secret, ConfigMap, MySQL, RabbitMQ, 6개 앱(리소스 포함), order-service HPA, Ingress, Prometheus, Grafana, Zipkin |

모든 리소스 이름에 `{{ .Release.Name }}` 접두사가 붙어 동일 클러스터에 `msa-shop`, `msa-shop-staging` 등 여러 릴리스를 설치할 수 있습니다.

## 검증

템플릿만 렌더링해 보기:

```bash
helm template msa-shop ./helm/msa-shop
```

배포 후 E2E는 `k8s/README.md`와 동일하게 port-forward 또는 Ingress URL로 실행:

```bash
kubectl port-forward svc/msa-shop-api-gateway 8080:8080
GATEWAY_URL=http://localhost:8080 ./scripts/e2e-flow.sh
```

(Helm으로 설치한 경우 서비스 이름이 `msa-shop-api-gateway` 형태이므로, 릴리스 이름이 `msa-shop`일 때 위와 같습니다.)

## 트러블슈팅

- **6단계(당일 매출) Gateway 경유 시 500, 직접 호출(8085)은 성공**  
  Gateway에 `SETTLEMENT_SERVICE_URI` 환경 변수가 없으면 `localhost:8085`로 프록시 시도 → 연결 실패 → 500.  
  `helm upgrade`로 최신 차트 적용:
  ```bash
  helm upgrade msa-shop ./helm/msa-shop
  ```
  적용 후 `kubectl get deployment msa-shop-api-gateway -o yaml | grep SETTLEMENT` 로 확인.

- **settlement-service 로그에 `RabbitMQ Connection refused`**  
  settlement-service가 RabbitMQ에 연결하지 못할 때 나옵니다. RabbitMQ Pod가 아직 준비되지 않았거나, settlement가 먼저 기동된 경우일 수 있습니다.

  ```bash
  # RabbitMQ Pod 상태 확인 (1/1 Running 이어야 함)
  kubectl get pods -l app=rabbitmq

  # RabbitMQ가 정상이면 settlement-service만 재시작해 연결 재시도
  kubectl rollout restart deployment/msa-shop-settlement-service
  ```

- **`Secret "msa-shop-secrets" ... cannot be imported ... missing key "app.kubernetes.io/managed-by"`**  
  `k8s/` 매니페스트로 이미 배포한 리소스와 이름이 겹칩니다. 기존 리소스를 지운 뒤 Helm으로 설치하세요.

  ```bash
  # k8s로 만든 리소스 전부 삭제 (같은 네임스페이스에서)
  kubectl delete -f k8s/

  # 필요 시 PVC만 남기고 싶으면 위 대신 아래처럼 개별 삭제
  # kubectl delete secret msa-shop-secrets configmap msa-shop-config ...
  # (MySQL PVC는 삭제하면 데이터가 사라짐)

  helm install msa-shop ./helm/msa-shop
  ```
