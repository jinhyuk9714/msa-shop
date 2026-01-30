#!/usr/bin/env bash
# E2E: 정산 API 시나리오 (일별/월별 목록·특정 일자·특정 월). GATEWAY_URL 또는 SETTLEMENT_URL.
set -e

if [ -n "$GATEWAY_URL" ]; then
  SETTLEMENT_URL="${SETTLEMENT_URL:-$GATEWAY_URL}"
else
  SETTLEMENT_URL="${SETTLEMENT_URL:-http://localhost:8085}"
fi

wait_for_ready() {
  local url="$1"
  local max=10 n=0
  while [ $n -lt $max ]; do
    if curl -sf "$url" >/dev/null 2>&1; then
      echo "[OK] settlement-service 준비됨"
      return 0
    fi
    n=$((n + 1))
    echo "  대기 중... ($n/$max)"
    sleep 2
  done
  echo "[실패] settlement-service 응답 없음"
  exit 1
}

echo "=== 서비스 준비 대기 ==="
wait_for_ready "$SETTLEMENT_URL/settlements/daily"
echo ""

echo "=== 1. GET /settlements/daily (파라미터 없음) → 200, 최근 일별 목록 ==="
RESP=$(curl -s -w "\n%{http_code}" "$SETTLEMENT_URL/settlements/daily")
CODE=$(echo "$RESP" | tail -n 1)
BODY=$(echo "$RESP" | sed '$d')
echo "HTTP $CODE"
if [ "$CODE" = "200" ]; then
  echo "$BODY" | python3 -m json.tool 2>/dev/null | head -20
  echo "[OK] 일별 목록 (배열)"
else
  echo "[참고] $CODE"
fi
echo ""

echo "=== 2. GET /settlements/daily?date=2020-01-01 (과거 일자) → 200 또는 404 ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$SETTLEMENT_URL/settlements/daily?date=2020-01-01")
echo "HTTP $CODE (데이터 없으면 404)"
echo ""

echo "=== 3. GET /settlements/monthly (파라미터 없음) → 200, 최근 월별 목록 ==="
RESP=$(curl -s -w "\n%{http_code}" "$SETTLEMENT_URL/settlements/monthly")
CODE=$(echo "$RESP" | tail -n 1)
echo "HTTP $CODE"
if [ "$CODE" = "200" ]; then
  echo "[OK] 월별 목록"
else
  echo "[참고] $CODE"
fi
echo ""

echo "=== 4. GET /settlements/monthly?yearMonth=2020-01 → 200 또는 404 ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$SETTLEMENT_URL/settlements/monthly?yearMonth=2020-01")
echo "HTTP $CODE"
echo ""

echo "=== 5. GET /settlements/daily?date= (잘못된 형식) → 400 가능 ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$SETTLEMENT_URL/settlements/daily?date=invalid")
echo "HTTP $CODE"
echo ""

echo "=== 완료 ==="
