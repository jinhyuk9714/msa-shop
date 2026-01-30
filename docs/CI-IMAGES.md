# CI 이미지 빌드 (ghcr.io)

main 브랜치에 push하면 GitHub Actions가 6개 서비스 Docker 이미지를 빌드해 **ghcr.io**에 푸시합니다.

## 워크플로

| 파일 | 트리거 | 동작 |
|------|--------|------|
| `.github/workflows/build-images.yml` | `main` push | 6개 서비스 이미지 빌드 → ghcr.io 푸시 |
| `.github/workflows/ci.yml` | `main` push, PR | 테스트 실행 |

두 워크플로는 **병렬**로 실행됩니다.

## 이미지 주소

- `ghcr.io/<owner>/msa-shop-api-gateway:latest`
- `ghcr.io/<owner>/msa-shop-user-service:latest`
- `ghcr.io/<owner>/msa-shop-product-service:latest`
- `ghcr.io/<owner>/msa-shop-order-service:latest`
- `ghcr.io/<owner>/msa-shop-payment-service:latest`
- `ghcr.io/<owner>/msa-shop-settlement-service:latest`

`<owner>`는 GitHub 저장소 소유자(예: jinhyuk9714)입니다.  
커밋 SHA 태그도 푸시됩니다: `ghcr.io/<owner>/msa-shop-api-gateway:<sha>`

## Helm으로 배포

**1) values-ghcr.yaml에서 owner 확인**

`helm/msa-shop/values-ghcr.yaml` 의 `jinhyuk9714` 를 본인 GitHub 사용자명으로 수정하세요.

**2) 설치·업그레이드**

```bash
# 최초 설치
helm install msa-shop ./helm/msa-shop -f helm/msa-shop/values-ghcr.yaml

# 업그레이드 (새 이미지 반영)
helm upgrade msa-shop ./helm/msa-shop -f helm/msa-shop/values-ghcr.yaml

# Pod 재시작 (imagePullPolicy: Always 로 자동 pull)
kubectl rollout restart deployment -l app.kubernetes.io/instance=msa-shop
```

## 레포지토리 권한

워크플로가 ghcr.io에 push하려면 **Workflow permissions** 설정이 필요합니다.

- GitHub 저장소 → **Settings** → **Actions** → **General**
- **Workflow permissions** → **Read and write permissions** 선택
- Save

## 비공개 이미지 사용 시 (클라우드 K8s)

ghcr.io 패키지가 비공개이면 K8s에서 `imagePullSecrets`가 필요합니다.

```bash
kubectl create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username=<GitHub사용자명> \
  --docker-password=<GitHub Personal Access Token (read:packages)>
```

Helm 설치 시:

```bash
helm upgrade msa-shop ./helm/msa-shop -f helm/msa-shop/values-ghcr.yaml \
  --set global.imagePullSecrets[0].name=ghcr-secret
```

(이를 위해 Helm 템플릿에 `imagePullSecrets` 지원 추가가 필요할 수 있습니다.)

## 이미지 공개 설정

ghcr.io 패키지 공개: GitHub → 프로필 → **Packages** → 해당 패키지 → **Package settings** → **Change visibility** → **Public**
