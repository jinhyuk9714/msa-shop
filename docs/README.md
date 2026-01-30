# MSA Shop 문서 목차

프로젝트 루트의 [README.md](../README.md)에서 시작하는 것이 좋습니다. 여기서는 `docs/` 내 문서를 용도별로 정리합니다.

---

## 실행·배포

| 문서 | 설명 |
|------|------|
| [RUN-LOCAL.md](RUN-LOCAL.md) | 로컬 실행(Gradle bootRun, Docker Compose), E2E 시나리오(일괄·429 방지), 환경 변수 |
| [OPENAPI.md](OPENAPI.md) | Swagger UI 접근 방법(로컬/K8s), 테스트 순서, Authorize·트러블슈팅 |
| [PROFILES-AND-SECRETS.md](PROFILES-AND-SECRETS.md) | Spring 프로파일(default/prod), 시크릿·환경 변수 관리 |

---

## 아키텍처·구현

| 문서 | 설명 |
|------|------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | 아키텍처 개요, 서비스 구성, 주문 플로우, 기술 스택 |
| [IMPLEMENTATION.md](IMPLEMENTATION.md) | 구현 현황, 모듈 구조, API·E2E·Docker 요약 |
| [API-SPEC.md](API-SPEC.md) | 서비스별 REST API 스펙 요약 |

---

## 장애·다음 단계

| 문서 | 설명 |
|------|------|
| [FAILURE-SCENARIOS.md](FAILURE-SCENARIOS.md) | 장애/실패 시나리오(JWT·재고·결제 등)와 대응 전략 |
| [NEXT-STEPS.md](NEXT-STEPS.md) | 완료 상태, 추천 진행 순서(프로덕션·기능 확장·성능) |

---

## CI/CD·K8s

| 문서 | 설명 |
|------|------|
| [CI-IMAGES.md](CI-IMAGES.md) | CI 이미지 빌드(ghcr.io), Helm 배포(values-ghcr) |
| [ARGOCD.md](ARGOCD.md) | Argo CD 연동, Git push → Helm 차트 자동 동기화 |
| [K8S-EXPANSION.md](K8S-EXPANSION.md) | K8s 확장 계획, ConfigMap·Secret·리소스 |

---

## 관련 (docs 외)

| 경로 | 설명 |
|------|------|
| [k8s/README.md](../k8s/README.md) | Kubernetes 매니페스트 적용 순서 |
| [helm/README.md](../helm/README.md) | Helm 차트 설치·업그레이드·리소스·HPA |
