#!/usr/bin/env bash
# E2E: 인증 관련 시나리오 (회원가입 중복 409, 로그인 실패, JWT 없이 인증 필요 API → 401, 유효 JWT → 200)
# GATEWAY_URL 설정 시 API Gateway 경유. K8s 사용 시 port-forward 필요.
set -e

if [ -n "$GATEWAY_URL" ]; then
  USER_URL="$GATEWAY_URL"
  ORDER_URL="$GATEWAY_URL"
else
  USER_URL="${USER_URL:-http://localhost:8081}"
  ORDER_URL="${ORDER_URL:-http://localhost:8083}"
fi

PASS=0
FAIL=0
check() { if [ "$1" = "$2" ]; then echo "[OK] HTTP $1"; PASS=$((PASS+1)); else echo "[FAIL] 기대 $2 실제 $1"; FAIL=$((FAIL+1)); fi; }

echo "=== 1. 회원가입 중복 이메일 → 409 ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$USER_URL/users" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test1234","name":"테스트"}')
check "$CODE" "409"
echo ""

echo "=== 2. 로그인 실패 (잘못된 비밀번호) → 401 또는 403 ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$USER_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"wrongpassword"}')
if [ "$CODE" = "401" ] || [ "$CODE" = "403" ]; then
  echo "[OK] HTTP $CODE (인증 실패)"
  PASS=$((PASS+1))
else
  echo "[FAIL] 기대 401/403 실제 $CODE"
  FAIL=$((FAIL+1))
fi
echo ""

echo "=== 3. GET /users/me (토큰 없음) → 401 ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$USER_URL/users/me")
check "$CODE" "401"
echo ""

echo "=== 4. 로그인 성공 → 200, 토큰 획득 ==="
LOGIN=$(curl -s -w "\n%{http_code}" -X POST "$USER_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test1234"}')
CODE=$(echo "$LOGIN" | tail -n 1)
BODY=$(echo "$LOGIN" | sed '$d')
check "$CODE" "200"
TOKEN=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null || true)
echo ""

echo "=== 5. GET /users/me (유효 토큰) → 200 ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" "$USER_URL/users/me")
check "$CODE" "200"
echo ""

echo "=== 6. POST /orders (토큰 없음) → 401 ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$ORDER_URL/orders" \
  -H "Content-Type: application/json" \
  -d '{"productId":1,"quantity":1,"paymentMethod":"CARD"}')
check "$CODE" "401"
echo ""

echo "=== 7. GET /orders/me (토큰 없음) → 401 ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$ORDER_URL/orders/me")
check "$CODE" "401"
echo ""

echo "=== 요약: OK=$PASS FAIL=$FAIL ==="
[ "$FAIL" -eq 0 ] && echo "인증 시나리오 모두 통과." || exit 1
