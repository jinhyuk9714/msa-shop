# Docker Desktop Kubernetes 정리

Docker Desktop에서 Kubernetes를 켜고 끌 때, 이전 클러스터·컨텍스트가 남아서 `kubectl`이나 Docker Desktop UI가 지저분해질 수 있습니다. 아래 순서대로 정리하면 됩니다.

---

## 1. kubeconfig 컨텍스트 정리

터미널에서 현재 등록된 컨텍스트를 확인합니다.

```bash
kubectl config get-contexts
```

Docker Desktop용 컨텍스트 이름은 보통 `docker-desktop` 또는 `docker-for-desktop` 입니다. 더 이상 쓰지 않을 컨텍스트는 삭제합니다.

```bash
# 예: docker-desktop 컨텍스트 삭제
kubectl config delete-context docker-desktop

# 다른 이름이면 해당 이름으로
kubectl config delete-context docker-for-desktop
```

클러스터/유저 항목만 남는 경우, 수동으로 지우려면:

```bash
kubectl config view
```

`~/.kube/config` 파일을 열어서 `contexts`, `clusters`, `users` 안에 사용하지 않는 `docker-desktop` 관련 블록을 삭제해도 됩니다. **현재 사용 중인 컨텍스트는 지우지 마세요.**

---

## 2. Docker Desktop에서 Kubernetes 클러스터 리셋

K8s는 쓰되, 클러스터 상태만 깨끗이 초기화하고 싶을 때:

1. Docker Desktop 실행
2. **설정(톱니바퀴)** → **Troubleshoot** (또는 상단 **Troubleshoot** 아이콘)
3. **Reset Kubernetes cluster** 클릭 후 확인

이렇게 하면 해당 클러스터의 Pod·Deployment·PV 등이 모두 삭제되고, 새 클러스터처럼 초기화됩니다. 인증서·kubeconfig는 Docker Desktop이 다시 잡아줍니다.

---

## 3. Kubernetes 완전히 끄고 정리 후 다시 켜기

K8s를 아예 안 쓰는 동안 깔끔하게 두고 싶다면:

1. **Docker Desktop** → **Settings** → **Kubernetes** → **Enable Kubernetes** 체크 해제 → **Apply & Restart**
2. 위 **1번**처럼 `kubectl config get-contexts` 후 `docker-desktop`(등) 컨텍스트 `delete-context`로 삭제
3. 필요하면 `~/.kube/config`에서 해당 cluster/user 항목도 삭제

다시 켤 때는 **Enable Kubernetes** 체크 → **Apply & Restart** 하면 새 컨텍스트가 생깁니다.

---

## 4. Docker Desktop 자체 초기화 (선택)

Kubernetes뿐 아니라 Docker Desktop 전체를 공장 초기화하고 싶다면:

1. **Settings** → **Troubleshoot** → **Reset to factory defaults** (또는 **Clean / Purge data**)
2. 확인 후 재시작

**주의:** 이미지, 볼륨, 네트워크 등 Docker 데이터가 모두 삭제됩니다. 필요한 이미지/볼륨은 미리 백업하거나 다시 받을 수 있는지 확인하세요.

---

## 요약

| 목표 | 방법 |
|------|------|
| 컨텍스트 목록만 정리 | `kubectl config delete-context docker-desktop` |
| 클러스터만 새로 시작 | Docker Desktop → Troubleshoot → **Reset Kubernetes cluster** |
| K8s 끄고 흔적 제거 | Kubernetes 비활성화 후 `delete-context` + `~/.kube/config` 수동 정리 |
| Docker 전체 초기화 | Troubleshoot → **Reset to factory defaults** |

이 프로젝트에서 K8s는 **Helm으로만** 쓰면 되므로, 사용 후에는 **Reset Kubernetes cluster**로 클러스터만 초기화해 두면 다음에 `helm install` 할 때 깔끔합니다.
