#!/usr/bin/env bash
# E2E: API Gateway Rate Limit (IP당 분당 N회 초과 시 429)
# GATEWAY_URL 이 설정되어 있어야 함 (예: http://localhost:8080). 직접 서비스 호출 시 Rate Limit 없음.
set -e

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
# 기본 120회 제한이면 130회 요청 시 429 확인. 빠른 테스트 시 Gateway에 RATE_LIMIT_PER_MINUTE=10 설정 후 LIMIT=12 등으로.
REQUESTS="${RATE_LIMIT_TEST_REQUESTS:-130}"
URL="$GATEWAY_URL/products"

echo "=== Rate Limit 테스트 (API Gateway) ==="
echo "Gateway: $GATEWAY_URL"
echo "연속 요청 수: ${REQUESTS} (기본 130 → 제한 120이면 429 발생)"
echo "요청 경로: $URL"
echo ""

# Gateway 준비 대기
for i in $(seq 1 5); do
  if curl -sf "$URL" >/dev/null 2>&1; then
    echo "[OK] Gateway 응답 확인"
    break
  fi
  [ $i -eq 5 ] && { echo "[실패] Gateway 미응답"; exit 1; }
  sleep 2
done
echo ""

echo "=== ${REQUESTS} 회 연속 요청 (429 발생 여부 확인) ==="
SUCCESS=0
RATE_LIMITED=0
for i in $(seq 1 "$REQUESTS"); do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$URL")
  if [ "$CODE" = "200" ]; then
    SUCCESS=$((SUCCESS + 1))
    printf "."
  elif [ "$CODE" = "429" ]; then
    RATE_LIMITED=$((RATE_LIMITED + 1))
    printf "X"
  else
    printf "?($CODE)"
  fi
done
echo ""

echo ""
echo "결과: 200=$SUCCESS, 429=$RATE_LIMITED"
if [ "$RATE_LIMITED" -ge 1 ]; then
  echo "[OK] Rate Limit 동작 확인 (429 수신)"
else
  echo "[참고] 429 미발생. Gateway 기본 제한(120/분)이면 요청 수가 부족할 수 있음."
  echo "       빠른 확인: docker compose 시 api-gateway에 RATE_LIMIT_PER_MINUTE=10 설정 후"
  echo "       RATE_LIMIT_TEST_REQUESTS=15 ./scripts/e2e-rate-limit.sh"
fi
