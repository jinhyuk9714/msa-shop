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
| OpenAPI/Swagger UI (6개 서비스) | ✅ |

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

- **6개 서비스**: user, product, order, payment, settlement, api-gateway — 모두 `/swagger-ui.html`, `/v3/api-docs` 제공
- **API Gateway**: WebFlux용 springdoc 추가, 라우팅 표·백엔드 Swagger 링크 안내
- **접근**: 로컬 `http://localhost:8081/swagger-ui.html` 등, K8s는 port-forward 후 동일
- 상세: [`docs/OPENAPI.md`](OPENAPI.md)

---

### 4. 프로덕션 준비

**목표:** 운영 환경에 맞게 보안·설정 보강

- **시크릿 관리**
  - Sealed Secrets, External Secrets Operator, Vault 등
  - `values-prod.yaml` 예시 및 문서화
- **이미지**
  - 레지스트리 URL·태그 전략 정리
  - `imagePullSecrets` 설정
- **리소스**
  - Deployment에 `resources.requests`/`limits` 설정
  - HPA(Horizontal Pod Autoscaler) 검토

---

### 5. 기능 확장

**목표:** 쇼핑몰 기능 확대

| 기능 | 설명 |
|------|------|
| ~~주문 취소 API~~ | ✅ `PATCH /orders/{id}/cancel` — PAID 주문 취소 (결제 취소 + 재고 복구) |
| 장바구니 서비스 | 별도 서비스 또는 order-service 확장 |
| 상품 카테고리 | product-service에 카테고리 필드/테이블 추가 |
| 상품 검색 | 이름/가격 조건 검색 API |
| 정산 API Gateway 노출 | `/settlements/**` JWT 검증 여부 정책화 (현재 인증 없음) |

---

### 6. 성능·안정성

| 항목 | 설명 |
|------|------|
| HPA | CPU/메모리 기반 Pod 자동 스케일링 |
| Redis 캐시 | 상품 목록·재고 등 캐싱 |
| Rate Limiting | API Gateway에 요청 제한 |
| DB 커넥션 풀 | HikariCP 설정 조정 |

---

## 빠르게 시도해볼 수 있는 것

- **Argo CD 설치·연동**: Git push 시 자동 K8s 배포
- **HPA 추가**: `kubectl autoscale deployment msa-shop-order-service --min=1 --max=3 --cpu-percent=70`
- **values-prod.yaml 작성**: 운영용 values 파일 예시

---

## 참고 문서

- [ARCHITECTURE.md](ARCHITECTURE.md) — 아키텍처 개요
- [K8S-EXPANSION.md](K8S-EXPANSION.md) — K8s 확장 계획
- [PROFILES-AND-SECRETS.md](PROFILES-AND-SECRETS.md) — 시크릿·프로파일
