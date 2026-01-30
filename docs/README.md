# MSA Shop 문서

루트 [README.md](../README.md)에서 프로젝트 개요와 실행 방법을 볼 수 있습니다.  
여기서는 **docs/** 안 문서를 용도별로 정리합니다.

---

## 시작하기

- **[RUN-LOCAL.md](RUN-LOCAL.md)** — 로컬 실행 순서(bootRun, Docker Compose), E2E 시나리오, 트러블슈팅
- **[INTERVIEW.md](INTERVIEW.md)** — 면접용 정리(한 줄/30초/1분 요약, 자주 나오는 질문·답변 포인트)
- **[OPENAPI.md](OPENAPI.md)** — Swagger UI 접근(로컬/K8s), Gateway 통합 `/api-docs`, Authorize 사용법
- **[PROFILES-AND-SECRETS.md](PROFILES-AND-SECRETS.md)** — Spring 프로파일(default/prod/local), 시크릿·환경 변수

---

## 아키텍처·API

- **[IMPLEMENTED-SUMMARY.md](IMPLEMENTED-SUMMARY.md)** — 구현된 것 전체 요약(서비스·API·인프라·E2E)
- **[ARCHITECTURE.md](ARCHITECTURE.md)** — 아키텍처 개요, 서비스 구성, 주문 플로우
- **[API-SPEC.md](API-SPEC.md)** — REST API 스펙 요약(요청/응답·상태코드)
- **[IMPLEMENTATION.md](IMPLEMENTATION.md)** — 구현 상세(모듈·도메인·E2E·Docker)

---

## 장애·설정

- **[FAILURE-SCENARIOS.md](FAILURE-SCENARIOS.md)** — 재고 부족·결제 실패·JWT 등 실패 시나리오와 대응
- **[NEXT-STEPS.md](NEXT-STEPS.md)** — 완료 상태, 다음에 할 수 있는 것(values-prod·Grafana·시크릿 등)
- **[SECRETS-OPERATIONS.md](SECRETS-OPERATIONS.md)** — 운영 시크릿 주입(Sealed Secrets, ESO, Vault)

---

## 배포·K8s

- **[CI-IMAGES.md](CI-IMAGES.md)** — GitHub Actions 이미지 빌드(ghcr.io), Helm(values-ghcr) 배포
- **[K8S-EXPANSION.md](K8S-EXPANSION.md)** — K8s 확장 계획(ConfigMap·Secret·리소스)
- **[DOCKER-DESKTOP-K8S-CLEANUP.md](DOCKER-DESKTOP-K8S-CLEANUP.md)** — Docker Desktop K8s 켜기/끄기 후 정리

**docs 외**

- [k8s/README.md](../k8s/README.md) — Kubernetes 매니페스트 적용 순서
- [helm/README.md](../helm/README.md) — Helm 차트 설치·업그레이드·HPA
