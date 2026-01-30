#!/usr/bin/env bash
# E2E: 404 시나리오 (없는 상품/주문 조회). 사전: 로그인 완료. GATEWAY_URL 설정 시 Gateway 경유.
set -e

if [ -n "$GATEWAY_URL" ]; then
  USER_URL="$GATEWAY_URL"
  PRODUCT_URL="$GATEWAY_URL"
  ORDER_URL="$GATEWAY_URL"
else
  USER_URL="${USER_URL:-http://localhost:8081}"
  PRODUCT_URL="${PRODUCT_URL:-http://localhost:8082}"
  ORDER_URL="${ORDER_URL:-http://localhost:8083}"
fi

echo "=== 로그인 (주문 404 테스트용) ==="
LOGIN=$(curl -s -X POST "$USER_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test1234"}')
TOKEN=$(echo "$LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null || true)
if [ -z "$TOKEN" ]; then
  echo "로그인 실패. e2e-flow.sh 로 회원가입·로그인 후 실행하세요."
  exit 1
fi
echo ""

echo "=== 1. GET /products/99999 (없는 상품) ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$PRODUCT_URL/products/99999")
echo "HTTP $CODE (기대: 404 또는 500, product-service 구현에 따름)"
if [ "$CODE" = "404" ] || [ "$CODE" = "500" ]; then
  echo "[OK] 없음 응답"
else
  echo "[참고] 예상 외 코드"
fi
echo ""

echo "=== 2. GET /orders/99999 (없는 주문, 유효 토큰) → 404 ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" "$ORDER_URL/orders/99999")
echo "HTTP $CODE"
if [ "$CODE" = "404" ]; then
  echo "[OK] 주문 없음 404"
else
  echo "[FAIL] 기대 404 실제 $CODE"
  exit 1
fi
echo ""

echo "=== 3. GET /orders/1 (존재할 수 있는 ID, 본인 주문이 아니면 404) ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" "$ORDER_URL/orders/1")
echo "HTTP $CODE (200=본인 주문 있음, 404=없음/타인 주문)"
echo ""

echo "=== 완료 ==="
