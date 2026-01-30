#!/usr/bin/env bash
# Helm으로 msa-shop 설치/업그레이드 후 E2E 검증 안내.
# 사용: ./scripts/helm-deploy.sh [--upgrade] [--values 파일] [--namespace ns] [--release 이름]
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CHART="$REPO_ROOT/helm/msa-shop"
RELEASE="${RELEASE:-msa-shop}"
NAMESPACE="${NAMESPACE:-default}"
UPGRADE=false
EXTRA_VALUES=()

while [[ $# -gt 0 ]]; do
  case $1 in
    --upgrade)   UPGRADE=true; shift ;;
    --values)    EXTRA_VALUES+=(-f "$2"); shift 2 ;;
    --namespace) NAMESPACE="$2"; shift 2 ;;
    --release)   RELEASE="$2"; shift 2 ;;
    *)           echo "Unknown option: $1"; exit 1 ;;
  esac
done

if ! command -v helm &>/dev/null; then
  echo "Helm이 필요합니다. https://helm.sh/docs/intro/install/"
  exit 1
fi

echo "=== Helm 배포 ==="
echo "릴리스: $RELEASE, 네임스페이스: $NAMESPACE"

if [ "$UPGRADE" = true ]; then
  helm upgrade "$RELEASE" "$CHART" \
    -n "$NAMESPACE" \
    -f "$CHART/values.yaml" \
    "${EXTRA_VALUES[@]:-}"
else
  helm install "$RELEASE" "$CHART" \
    -n "$NAMESPACE" \
    --create-namespace \
    -f "$CHART/values.yaml" \
    "${EXTRA_VALUES[@]:-}"
fi

echo ""
echo "=== 설치 후 할 일 ==="
echo "1) Pod 준비 대기:"
echo "   kubectl get pods -n $NAMESPACE -w"
echo ""
echo "2) API Gateway port-forward (다른 터미널에서 유지):"
echo "   kubectl port-forward svc/${RELEASE}-api-gateway 8080:8080 -n $NAMESPACE"
echo ""
echo "3) E2E 검증:"
echo "   GATEWAY_URL=http://localhost:8080 ./scripts/e2e-flow.sh"
echo "   GATEWAY_URL=http://localhost:8080 ./scripts/e2e-all-scenarios.sh"
echo ""
echo "   일괄 E2E 시 429를 피하려면 api-gateway에 RATE_LIMIT_PER_MINUTE=0 설정 후 배포하거나,"
echo "   helm upgrade 시 --set apiGateway.env.RATE_LIMIT_PER_MINUTE=0 적용."
echo ""
echo "4) 관측성 (선택):"
echo "   kubectl port-forward svc/${RELEASE}-prometheus 9090:9090 -n $NAMESPACE   # Prometheus"
echo "   kubectl port-forward svc/${RELEASE}-grafana 3000:3000 -n $NAMESPACE     # Grafana (admin/admin)"
echo "   kubectl port-forward svc/${RELEASE}-zipkin 9411:9411 -n $NAMESPACE     # Zipkin"
