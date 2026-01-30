#!/usr/bin/env bash
# E2E 전체 시나리오 일괄 실행. GATEWAY_URL 설정 시 Gateway 경유 (K8s 사용 시 port-forward 후 실행).
# 사전: e2e-flow.sh 로 회원가입·로그인 완료 (test@test.com / test1234)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

export GATEWAY_URL="${GATEWAY_URL:-}"

run_one() {
  local name="$1"
  local script="$2"
  echo ""
  echo "########################################"
  echo "# $name"
  echo "########################################"
  if [ -x "$script" ]; then
    if "$script"; then
      echo "[통과] $name"
      return 0
    else
      echo "[실패] $name"
      return 1
    fi
  else
    echo "[스킵] $script 없음 또는 실행 불가"
    return 0
  fi
}

FAILED=0

run_one "1. 기본 흐름 (상품→가입→로그인→주문→조회→정산)" "./scripts/e2e-flow.sh" || FAILED=$((FAILED+1))
run_one "2. 인증 (중복 409, 로그인 실패 401, JWT 없음 401, 유효 JWT 200)" "./scripts/e2e-auth-scenarios.sh" || FAILED=$((FAILED+1))
run_one "3. 주문 플로우 (생성→단건 조회→내 주문 목록)" "./scripts/e2e-order-scenarios.sh" || FAILED=$((FAILED+1))
run_one "4. 실패 시나리오 (재고 부족 409, 취소 성공·재취소 409)" "./scripts/e2e-failure-scenarios.sh" || FAILED=$((FAILED+1))
run_one "5. 404 (없는 주문 조회)" "./scripts/e2e-not-found-scenarios.sh" || FAILED=$((FAILED+1))
run_one "6. 상품 검색 (name, minPrice, maxPrice)" "./scripts/e2e-product-search.sh" || FAILED=$((FAILED+1))
run_one "7. 정산 (일별/월별 목록·특정 일자·월)" "./scripts/e2e-settlement-scenarios.sh" || FAILED=$((FAILED+1))

if [ -n "$GATEWAY_URL" ]; then
  run_one "8. Rate Limit (429)" "./scripts/e2e-rate-limit.sh" || FAILED=$((FAILED+1))
else
  echo ""
  echo "########################################"
  echo "# 8. Rate Limit (스킵: GATEWAY_URL 미설정)"
  echo "########################################"
  echo "GATEWAY_URL=http://localhost:8080 ./scripts/e2e-rate-limit.sh 로 별도 실행"
fi

echo ""
echo "========================================"
if [ "$FAILED" -eq 0 ]; then
  echo "전체 시나리오 통과."
else
  echo "실패: $FAILED 개"
  exit 1
fi
