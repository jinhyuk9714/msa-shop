#!/usr/bin/env bash
set -e

USER_URL="${USER_URL:-http://localhost:8081}"
PRODUCT_URL="${PRODUCT_URL:-http://localhost:8082}"
ORDER_URL="${ORDER_URL:-http://localhost:8083}"

echo "=== 1. 상품 목록 확인 (product-service) ==="
curl -s "$PRODUCT_URL/products" | python3 -m json.tool || true
echo ""

echo "=== 2. 회원가입 (user-service) ==="
REG_RESP=$(curl -s -w "\n%{http_code}" -X POST "$USER_URL/users" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test1234","name":"테스트 유저"}')
REG_BODY=$(echo "$REG_RESP" | sed '$d')
REG_CODE=$(echo "$REG_RESP" | tail -n 1)
if [ "$REG_CODE" = "201" ]; then
  echo "$REG_BODY" | python3 -m json.tool
else
  echo "HTTP $REG_CODE (이미 가입된 계정이면 로그인으로 진행)"
  [ -n "$REG_BODY" ] && echo "$REG_BODY" | python3 -m json.tool 2>/dev/null || echo "$REG_BODY"
fi
echo ""

echo "=== 3. 로그인 → 액세스 토큰 발급 ==="
LOGIN_RESP=$(curl -s -X POST "$USER_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test1234"}')
echo "$LOGIN_RESP" | python3 -m json.tool
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))")
if [ -z "$TOKEN" ]; then
  echo "로그인 실패: accessToken 없음"
  exit 1
fi
echo "Token: $TOKEN"
echo ""

echo "=== 4. 주문 생성 (order-service) — productId=1, quantity=2 ==="
ORDER_RESP=$(curl -s -w "\n%{http_code}" -X POST "$ORDER_URL/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":1,"quantity":2,"paymentMethod":"CARD"}')
HTTP_BODY=$(echo "$ORDER_RESP" | sed '$d')
HTTP_CODE=$(echo "$ORDER_RESP" | tail -n 1)
echo "HTTP $HTTP_CODE"
echo "$HTTP_BODY" | python3 -m json.tool 2>/dev/null || echo "$HTTP_BODY"
echo ""

if [ "$HTTP_CODE" = "201" ]; then
  ORDER_ID=$(echo "$HTTP_BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))")
  if [ -n "$ORDER_ID" ]; then
    echo "=== 5. 주문 단건 조회 GET /orders/$ORDER_ID ==="
    curl -s "$ORDER_URL/orders/$ORDER_ID" | python3 -m json.tool
  fi
else
  echo "주문 실패 (HTTP $HTTP_CODE). 재고 부족/결제 실패 등 확인."
fi
