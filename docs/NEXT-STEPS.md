# 다음 단계 가이드

현재까지 완료된 작업 기준으로, 이어서 진행할 수 있는 단계들을 정리했습니다.

---

## 현재 완료 상태

| 영역 | 상태 |
|------|------|
| 6개 서비스 (user, product, order, payment, settlement, api-gateway) | ✅ |
| Docker Compose | ✅ |
| Kubernetes (k8s/) | ✅ |
| Helm 차트 | ✅ |
| E2E 흐름 (6단계) | ✅ |
| CI (GitHub Actions) | ✅ |
| Testcontainers 통합 테스트 | ✅ |
| 프로파일·시크릿 (prod) | ✅ |
| Prometheus·Grafana·Zipkin (K8s/Helm) | ✅ |
| OpenAPI/Swagger UI (5개 앱, api-docs.html) | ✅ |
| Deployment 리소스·order-service HPA (Helm) | ✅ |

---

## 추천 진행 순서

### 1. K8s/Helm에 관측성 스택 추가 ✅ 완료

**구현 내용:** Helm 차트에 Prometheus, Grafana, Zipkin 추가 (`observability.enabled: true` 기본)

- **Prometheus**: 6개 앱 `/actuator/prometheus` 15초 간격 스크래핑
- **Grafana**: Prometheus 데이터소스 자동 등록, admin/admin
- **Zipkin**: 분산 추적 (api-gateway, product, order, payment 서비스 연동)
- 접속: `kubectl port-forward svc/msa-shop-prometheus 9090:9090` 등 (상세는 `helm/README.md`)

---

### 2. CI/CD 확장 ✅ 완료

**구현 내용:** main 푸시 시 6개 서비스 이미지 자동 빌드 → ghcr.io 푸시

- `.github/workflows/build-images.yml` — 빌드·푸시
- `helm/msa-shop/values-ghcr.yaml` — ghcr.io 이미지로 Helm 배포
- 상세: [`docs/CI-IMAGES.md`](CI-IMAGES.md)

---

### 3. API 문서 자동화 (Swagger/OpenAPI) ✅ 완료

**구현 내용:** springdoc-openapi로 각 서비스 Swagger UI 노출

- **5개 앱 서비스**: user, product, order, payment, settlement — `/api-docs.html` (Swagger UI), `/v3/api-docs`. API Gateway는 Swagger UI 없음.
- **접근**: 로컬 `http://localhost:8081/api-docs.html` 등, K8s는 port-forward 후 동일
- 상세·테스트 순서: [`docs/OPENAPI.md`](OPENAPI.md)

---

### 4. 프로덕션 준비 ✅ 일부 완료

**목표:** 운영 환경에 맞게 보안·설정 보강

- **시크릿 관리**: Sealed Secrets, External Secrets Operator, Vault 등 (운영 시 도입 권장)
- **values-prod.yaml** ✅ 예시 추가. 이미지·태그·리소스·Ingress 호스트·imagePullSecrets 안내. `helm install ... -f values.yaml -f values-prod.yaml`
- **이미지**: 레지스트리·태그 전략 ✅ values-prod 예시. **imagePullSecrets** ✅ Helm 템플릿·values-prod 주석
- **리소스** ✅ Helm 차트 반영. **HPA** ✅ order-service 기본 활성화. `helm/README.md` 참고.

---

### 5. 기능 확장

**목표:** 쇼핑몰 기능 확대

| 기능 | 설명 |
|------|------|
| ~~주문 취소 API~~ | ✅ `PATCH /orders/{id}/cancel` — PAID 주문 취소 (결제 취소 + 재고 복구) |
| 장바구니 서비스 | 별도 서비스 또는 order-service 확장 |
| 상품 카테고리 | product-service에 카테고리 필드/테이블 추가 |
| ~~상품 검색~~ | ✅ `GET /products?name=...&minPrice=...&maxPrice=...` (이름 부분 일치, 가격 범위) |
| ~~정산 API Gateway 노출~~ | ✅ `app.settlements.auth-required`(기본 false). true면 `/settlements/**` JWT 필수. Helm: `apiGateway.settlementsAuthRequired` |

---

### 6. 성능·안정성

| 항목 | 설명 |
|------|------|
| HPA | ✅ order-service CPU 기반 (Helm 기본) |
| ~~Redis 캐시~~ | ✅ product-service 상품 목록·단건 조회 Redis 캐시. 재고 변경 시 evict. Docker/Helm Redis 포함. 로컬: profile local 시 Redis 제외 |
| Rate Limiting | ✅ API Gateway 인메모리 Rate Limit (IP당 분당 120회, 429 초과 시). `app.rate-limit.per-minute`, `/actuator/health` 제외 |
| DB 커넥션 풀 | HikariCP 설정 조정 (미구현) |

---

## 빠르게 시도해볼 수 있는 것

- **Argo CD 설치·연동**: ✅ `argocd/application.yaml` 추가. Git push 시 Helm 차트 자동 동기화. [docs/ARGOCD.md](ARGOCD.md)
- **HPA**: Helm 기본값으로 order-service HPA 활성화됨. 비활성화 시 `--set orderService.hpa.enabled=false`. [`helm/README.md`](../helm/README.md) 참고.
- **values-prod.yaml 작성**: 운영용 values 파일 예시

---

## 참고 문서

- [ARCHITECTURE.md](ARCHITECTURE.md) — 아키텍처 개요
- [K8S-EXPANSION.md](K8S-EXPANSION.md) — K8s 확장 계획
- [PROFILES-AND-SECRETS.md](PROFILES-AND-SECRETS.md) — 시크릿·프로파일
