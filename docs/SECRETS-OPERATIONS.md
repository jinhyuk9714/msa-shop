# 운영 시크릿 주입 방법

운영 환경에서 JWT·DB·RabbitMQ 비밀을 Git/values에 넣지 않고 주입하는 방법을 정리합니다.

---

## 필요한 비밀 항목

| 항목 | Helm values 키 | 사용처 |
|------|----------------|--------|
| MySQL root 비밀번호 | `secrets.mysqlRootPassword` | 5개 앱 DB 접속 |
| JWT 시크릿 (32자 이상) | `secrets.jwtSecret` | user, order, api-gateway |
| RabbitMQ 사용자/비밀번호 | `secrets.rabbitmqUsername`, `rabbitmqPassword` | payment, settlement |

---

## 1. Helm values에 직접 넣지 않기

- **values-prod.yaml**에는 비밀을 쓰지 말고, 아래 도구로 생성된 Secret을 쓰거나 **CI에서만** 주입합니다.
- 기본 Helm 차트는 `templates/secret.yaml`이 `values.secrets.*`를 참조하므로, **운영 시**에는 이 템플릿을 쓰지 않고 **외부에서 만든 Secret**을 쓰는 방식을 권장합니다.

---

## 2. 운영 시 권장 방식 (택 1)

### A. Sealed Secrets

- **역할**: Git에 암호화된 Secret만 저장하고, 클러스터에서 Sealed Secrets 컨트롤러가 복호화해 일반 Secret 생성.
- **흐름**: 로컬에서 `kubeseal`로 Secret을 암호화 → 암호화된 YAML을 Git에 커밋 → 클러스터에서 자동 복호화.
- **Helm과 함께**: Helm 차트에서 Secret 리소스를 만들지 않고(`secret.yaml` 조건부 비활성화 또는 별도 관리), Sealed Secret 리소스만 적용. Pod는 기존처럼 `secretKeyRef`로 동일한 이름의 Secret 참조.

### B. External Secrets Operator (ESO)

- **역할**: AWS Secrets Manager, GCP Secret Manager, Vault, Kubernetes Secret 등 **외부 저장소**에서 값을 읽어와 K8s Secret으로 동기화.
- **흐름**: ExternalSecret 리소스에 "어디서 어떤 키를 가져올지" 정의 → ESO가 주기적으로 동기화해 Secret 생성.
- **Helm과 함께**: Helm에서 Secret 대신 ExternalSecret 리소스를 배포하거나, values로 ESO용 SecretStore/ExternalSecret 매니페스트 경로만 지정.

### C. Vault (Hashicorp)

- **역할**: 비밀 저장·발급 전용 서버. 앱이 Vault API로 런타임에 비밀 조회하거나, Vault Agent가 파일/환경 변수로 주입.
- **Helm과 함께**: Pod에 Vault Agent 사이드카를 넣어서 `APP_JWT_SECRET` 등 env를 주입하거나, Vault → K8s Secret 동기화 도구와 조합.

### D. CI에서만 주입 (간단한 운영)

- **역할**: GitHub Actions 등 CI에서 `helm install/upgrade` 시 `--set secrets.jwtSecret=${{ secrets.JWT_SECRET }}` 형태로 한 번만 넘김. values 파일에는 비밀 없음.
- **주의**: CI 로그·캐시에 값이 남지 않도록 하고, 저장소의 GitHub Secrets 등에만 보관.

---

## 3. values-prod에서 할 일

- **이미지·리소스·Ingress** 등은 `values-prod.yaml`에 두고, **비밀만** 아래 중 하나로 처리합니다.
  - **Sealed Secrets / ESO / Vault** 사용 시: Helm `secret.yaml`을 비활성화하거나, 시크릿 이름을 `msa-shop-secrets`로 맞춰 미리 만들어 두고 Helm은 Secret 리소스를 생성하지 않도록 수정.
  - **CI 주입** 사용 시: `helm install ... -f values-prod.yaml --set secrets.jwtSecret=... --set secrets.mysqlRootPassword=...` (실제 값은 CI 변수에서만).
- **Grafana admin 비밀번호** 등도 동일하게 시크릿 저장소 또는 CI에서만 주입하는 것을 권장합니다.

---

## 4. 참고

- [PROFILES-AND-SECRETS.md](PROFILES-AND-SECRETS.md) — 프로파일·환경 변수 요약
- [helm/README.md](../helm/README.md) — Helm 설치·values-prod 사용
