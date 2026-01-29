# Kubernetes 배포 예시

> 포트폴리오용 최소 매니페스트. 실제 운영 시 Secret·ConfigMap·Ingress·헬스체크 등 보강 필요.

## 전제

- Docker 이미지는 이미 빌드·레지스트리에 푸시된 상태로 가정.
- MySQL, RabbitMQ 등은 클러스터 내부 또는 외부 서비스로 준비. 예시 yaml의 DB URL·비밀번호는 로컬 테스트용(실제로는 Secret 사용 권장).

## order-service 예시

- `order-service.yaml`: Deployment + Service (ClusterIP).
- 이미지: `your-registry/msa-shop-order-service:latest` 등으로 교체 후 적용.

```bash
# 이미지 태그만 교체 후 적용 예시
kubectl apply -f k8s/order-service.yaml

# 상태 확인
kubectl get pods -l app=order-service
kubectl get svc order-service
```

## 전체 스택

실제로는 아래가 모두 필요합니다.

- **인프라**: MySQL, RabbitMQ (StatefulSet 또는 외부 관리형 서비스).
- **앱**: api-gateway, user-service, product-service, order-service, payment-service, settlement-service 각각 Deployment + Service.
- **설정**: ConfigMap( base-url 등), Secret( DB 비밀번호, JWT secret ).
- **노출**: Ingress 또는 LoadBalancer Service (Gateway 8080).

상세는 프로젝트 루트 `docs/RUN-LOCAL.md`, `docs/IMPLEMENTATION.md` 참고.
