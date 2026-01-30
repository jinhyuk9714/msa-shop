# Argo CD 연동

Git push 시 Helm 차트를 Kubernetes에 자동 동기화하려면 Argo CD를 사용할 수 있습니다.

## 전제

- Kubernetes 클러스터에 Argo CD가 설치되어 있음
- 이 저장소(Git)에 push 권한이 있음
- CI로 이미지가 빌드·푸시된 경우, Argo CD는 Helm 차트(매니페스트)만 동기화하고, 이미지는 기존 CI(GitHub Actions 등)에서 빌드

## Argo CD 설치 (미설치 시)

```bash
# 예: kubectl로 설치
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# UI 접속 (port-forward)
kubectl port-forward svc/argocd-server -n argocd 8080:443
# 브라우저: https://localhost:8080 (초기 비밀번호: kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d)
```

## msa-shop Application 적용

1. **저장소 URL 수정**  
   `argocd/application.yaml`의 `spec.source.repoURL`을 본인 Git 저장소 URL로 수정합니다.

2. **Application 생성**

```bash
kubectl apply -f argocd/application.yaml
```

3. **동기화**  
   Argo CD UI에서 `msa-shop` 앱을 선택 후 **Sync** 하거나, `syncPolicy.automated`가 있으면 push 시 자동 동기화됩니다.

## values 선택

- **기본(로컬 이미지)**: `values.yaml`만 사용 (이미지는 미리 빌드·로드 필요)
- **ghcr.io 이미지**: Helm의 `valueFiles`에 `values-ghcr.yaml` 추가. `values-ghcr.yaml`의 `owner`를 본인 GitHub 사용자명으로 수정
- **프로덕션**: `valueFiles`에 `values-prod.yaml` 추가 후, 이미지·시크릿·Ingress 호스트 등 실제 값으로 수정

`argocd/application.yaml`에서 `spec.source.helm.valueFiles`를 필요한 대로 수정한 뒤 적용하면 됩니다.

## 동작 흐름

1. Git 저장소의 `helm/msa-shop` 경로(Helm 차트)를 Argo CD가 주기적으로 조회
2. `main`(또는 `targetRevision`) 브랜치 변경 시 차트를 렌더링
3. `destination.namespace`(기본 `msa-shop`)에 리소스 생성·업데이트
4. `syncPolicy.automated`가 있으면 Git과 클러스터 상태를 자동으로 맞춤

## 연동 후 확인 절차 (체크리스트)

1. **Argo CD 설치**  
   `kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml` 후 Pod가 모두 Running인지 확인.

2. **저장소 등록**  
   공개 Git이면 별도 등록 없이 Application에서 바로 사용. 비공개면 Argo CD UI **Settings → Repositories**에서 URL·인증 정보 추가.

3. **Application 적용**  
   `argocd/application.yaml`의 `repoURL`을 본인 저장소로 수정 후 `kubectl apply -f argocd/application.yaml`.

4. **동기화 확인**  
   Argo CD UI에서 `msa-shop` 앱 선택 → **Sync** (또는 자동 sync 대기). **Healthy**·**Synced** 상태가 되면 배포 완료.

5. **Pod 준비 대기**  
   `kubectl get pods -n msa-shop -w` 로 MySQL·RabbitMQ·Redis·6개 앱이 모두 Running 될 때까지 대기.

6. **E2E 검증**  
   `kubectl port-forward svc/msa-shop-api-gateway 8080:8080 -n msa-shop` (별도 터미널 유지)  
   → `GATEWAY_URL=http://localhost:8080 ./scripts/e2e-flow.sh` 또는 `./scripts/e2e-all-scenarios.sh`.

**"Application referencing project default which does not exist"** 가 나오면 default 프로젝트가 없는 것입니다. 한 번 적용:

```bash
kubectl apply -f argocd/default-project.yaml
```

이후 Argo CD UI에서 msa-shop 앱이 Healthy로 바뀔 수 있습니다.

문제 시: Argo CD UI에서 **Application Details → Events** 또는 `kubectl get applications -n argocd` 로 상태 확인.

## 참고

- [Argo CD 문서](https://argo-cd.readthedocs.io/)
- [Helm 차트 설치](helm/README.md) — Argo CD 없이 수동 설치 시
