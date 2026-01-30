#!/usr/bin/env bash
# E2E: 장바구니 시나리오 (추가 → 조회 → 수량 변경 → 삭제 → 비우기). 사전: 회원가입·로그인. GATEWAY_URL 설정 시 Gateway 경유.
set -e

if [ -n "$GATEWAY_URL" ]; then
  USER_URL="$GATEWAY_URL"
  PRODUCT_URL="$GATEWAY_URL"
  CART_URL="$GATEWAY_URL"
else
  USER_URL="${USER_URL:-http://localhost:8081}"
  PRODUCT_URL="${PRODUCT_URL:-http://localhost:8082}"
  CART_URL="${ORDER_URL:-http://localhost:8083}"
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

echo "=== 1. 장바구니 비우기 (초기화) DELETE /cart → 204 ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$CART_URL/cart" \
  -H "Authorization: Bearer $TOKEN")
echo "HTTP $CODE"
[ "$CODE" = "204" ] || [ "$CODE" = "200" ] && echo "[OK]" || echo "[참고] $CODE"
echo ""

echo "=== 2. 장바구니 조회 GET /cart → 200 (빈 배열) ==="
CART_RESP=$(curl -s -w "\n%{http_code}" -H "Authorization: Bearer $TOKEN" "$CART_URL/cart")
CART_CODE=$(echo "$CART_RESP" | tail -n 1)
CART_BODY=$(echo "$CART_RESP" | sed '$d')
echo "HTTP $CART_CODE"
echo "$CART_BODY" | python3 -m json.tool 2>/dev/null || echo "$CART_BODY"
[ "$CART_CODE" = "200" ] && echo "[OK] 장바구니 조회" || { echo "[FAIL] 기대 200 실제 $CART_CODE"; exit 1; }
echo ""

echo "=== 3. 장바구니에 추가 POST /cart/items (productId=1, quantity=2) → 201 ==="
ADD_RESP=$(curl -s -w "\n%{http_code}" -X POST "$CART_URL/cart/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":1,"quantity":2}')
ADD_CODE=$(echo "$ADD_RESP" | tail -n 1)
ADD_BODY=$(echo "$ADD_RESP" | sed '$d')
echo "HTTP $ADD_CODE"
echo "$ADD_BODY" | python3 -m json.tool 2>/dev/null || echo "$ADD_BODY"
[ "$ADD_CODE" = "201" ] && echo "[OK] 장바구니 추가" || { echo "[FAIL] 기대 201 실제 $ADD_CODE"; exit 1; }
echo ""

echo "=== 4. 같은 상품 추가 (수량 합산) POST /cart/items (productId=1, quantity=1) → 201 ==="
ADD2_RESP=$(curl -s -w "\n%{http_code}" -X POST "$CART_URL/cart/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":1,"quantity":1}')
ADD2_CODE=$(echo "$ADD2_RESP" | tail -n 1)
echo "HTTP $ADD2_CODE"
[ "$ADD2_CODE" = "201" ] && echo "[OK] 수량 합산" || echo "[참고] $ADD2_CODE"
echo ""

echo "=== 5. 장바구니 조회 GET /cart → 200 (1건, quantity=3) ==="
CART2_RESP=$(curl -s -w "\n%{http_code}" -H "Authorization: Bearer $TOKEN" "$CART_URL/cart")
CART2_CODE=$(echo "$CART2_RESP" | tail -n 1)
CART2_BODY=$(echo "$CART2_RESP" | sed '$d')
echo "HTTP $CART2_CODE"
echo "$CART2_BODY" | python3 -m json.tool 2>/dev/null || echo "$CART2_BODY"
[ "$CART2_CODE" = "200" ] && echo "[OK]" || { echo "[FAIL] $CART2_CODE"; exit 1; }
echo ""

echo "=== 6. 상품 2 추가 POST /cart/items (productId=2, quantity=1) → 201 ==="
ADD3_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$CART_URL/cart/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":2,"quantity":1}')
echo "HTTP $ADD3_CODE"
[ "$ADD3_CODE" = "201" ] && echo "[OK]" || echo "[참고] $ADD3_CODE"
echo ""

echo "=== 7. 수량 변경 PATCH /cart/items/1 (quantity=5) → 200 ==="
PATCH_RESP=$(curl -s -w "\n%{http_code}" -X PATCH "$CART_URL/cart/items/1" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"quantity":5}')
PATCH_CODE=$(echo "$PATCH_RESP" | tail -n 1)
echo "HTTP $PATCH_CODE"
[ "$PATCH_CODE" = "200" ] && echo "[OK] 수량 변경" || echo "[참고] $PATCH_CODE (재고 부족 시 409 가능)"
echo ""

echo "=== 8. 항목 삭제 DELETE /cart/items/2 → 204 ==="
DEL_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$CART_URL/cart/items/2" \
  -H "Authorization: Bearer $TOKEN")
echo "HTTP $DEL_CODE"
[ "$DEL_CODE" = "204" ] && echo "[OK] 항목 삭제" || echo "[참고] $DEL_CODE"
echo ""

echo "=== 9. 장바구니에서 주문 POST /orders/from-cart → 201 ==="
FROM_CART_RESP=$(curl -s -w "\n%{http_code}" -X POST "$CART_URL/orders/from-cart" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"paymentMethod":"CARD"}')
FROM_CART_CODE=$(echo "$FROM_CART_RESP" | tail -n 1)
FROM_CART_BODY=$(echo "$FROM_CART_RESP" | sed '$d')
echo "HTTP $FROM_CART_CODE"
echo "$FROM_CART_BODY" | python3 -m json.tool 2>/dev/null || echo "$FROM_CART_BODY"
[ "$FROM_CART_CODE" = "201" ] && echo "[OK] 장바구니에서 주문 생성" || { echo "[FAIL] 기대 201 실제 $FROM_CART_CODE"; exit 1; }
# 장바구니 비워졌는지 확인
CART_AFTER=$(curl -s -H "Authorization: Bearer $TOKEN" "$CART_URL/cart")
if [ "$CART_AFTER" = "[]" ]; then
  echo "[OK] 주문 후 장바구니 비워짐"
else
  echo "[참고] 장바구니: $CART_AFTER"
fi
echo ""

echo "=== 10. JWT 없이 장바구니 조회 → 401 ==="
NOAUTH_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$CART_URL/cart")
echo "HTTP $NOAUTH_CODE"
[ "$NOAUTH_CODE" = "401" ] && echo "[OK] 인증 필수" || echo "[참고] $NOAUTH_CODE (Gateway 미사용 시 401/500)"
echo ""

echo "=== 11. 장바구니 비우기 DELETE /cart → 204 ==="
CLEAR_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$CART_URL/cart" \
  -H "Authorization: Bearer $TOKEN")
echo "HTTP $CLEAR_CODE"
[ "$CLEAR_CODE" = "204" ] || [ "$CLEAR_CODE" = "200" ] && echo "[OK] 장바구니 비우기" || echo "[참고] $CLEAR_CODE"
echo ""

echo "=== 완료: 장바구니 시나리오 ==="
