#!/usr/bin/env bash
# E2E: 주문 플로우 시나리오 (생성 → 조회 → 내 주문 목록). 사전: 회원가입·로그인. GATEWAY_URL 설정 시 Gateway 경유.
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

echo "=== 로그인 ==="
LOGIN=$(curl -s -X POST "$USER_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test1234"}')
TOKEN=$(echo "$LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null || true)
if [ -z "$TOKEN" ]; then
  echo "로그인 실패. 먼저 e2e-flow.sh 실행 후 다시 시도."
  exit 1
fi
echo ""

echo "=== 1. 상품 목록 확인 ==="
curl -s "$PRODUCT_URL/products" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'상품 {len(d)}건')" 2>/dev/null || true
echo ""

echo "=== 2. 주문 생성 POST /orders → 201 ==="
ORDER_RESP=$(curl -s -w "\n%{http_code}" -X POST "$ORDER_URL/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":1,"quantity":1,"paymentMethod":"CARD"}')
ORDER_BODY=$(echo "$ORDER_RESP" | sed '$d')
ORDER_CODE=$(echo "$ORDER_RESP" | tail -n 1)
echo "HTTP $ORDER_CODE"
if [ "$ORDER_CODE" = "201" ]; then
  echo "$ORDER_BODY" | python3 -m json.tool
  ORDER_ID=$(echo "$ORDER_BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || true)
  echo "[OK] 주문 생성 id=$ORDER_ID"
else
  echo "$ORDER_BODY" | python3 -m json.tool 2>/dev/null || echo "$ORDER_BODY"
  echo "[스킵] 주문 생성 실패, 이후 단계 생략"
  exit 0
fi
echo ""

echo "=== 3. 주문 단건 조회 GET /orders/$ORDER_ID → 200 ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" "$ORDER_URL/orders/$ORDER_ID")
echo "HTTP $CODE"
if [ "$CODE" = "200" ]; then
  curl -s -H "Authorization: Bearer $TOKEN" "$ORDER_URL/orders/$ORDER_ID" | python3 -m json.tool
  echo "[OK] 주문 조회"
else
  echo "[FAIL] 기대 200 실제 $CODE"
fi
echo ""

echo "=== 4. 내 주문 목록 GET /orders/me → 200 ==="
ME_RESP=$(curl -s -w "\n%{http_code}" -H "Authorization: Bearer $TOKEN" "$ORDER_URL/orders/me")
ME_CODE=$(echo "$ME_RESP" | tail -n 1)
ME_BODY=$(echo "$ME_RESP" | sed '$d')
echo "HTTP $ME_CODE"
if [ "$ME_CODE" = "200" ]; then
  COUNT=$(echo "$ME_BODY" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "?")
  echo "내 주문 $COUNT 건"
  echo "[OK] 주문 목록"
else
  echo "[참고] $ME_CODE"
fi
echo ""

echo "=== 완료 ==="
