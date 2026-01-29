#!/usr/bin/env bash
# E2E 시나리오: 상품 목록 → 회원가입 → 로그인 → 주문 생성 → 주문 조회.
# GATEWAY_URL 설정 시 API Gateway(8080) 경유. 미설정 시 각 서비스 직접 호출(8081~8083).
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
# settlement-service는 Gateway 경로 없음. 직접 8085 사용.
SETTLEMENT_URL="${SETTLEMENT_URL:-http://localhost:8085}"

# 서비스 준비 대기 (Docker Compose 직후 Spring Boot 기동 시간 확보)
wait_for_ready() {
  local url="$1"
  local name="$2"
  local max=10
  local n=0
  while [ $n -lt $max ]; do
    if curl -sf "$url" >/dev/null 2>&1 || true; then
      local body
      body=$(curl -s "$url" 2>/dev/null || true)
      if [ -n "$body" ] && echo "$body" | python3 -c "import sys,json; json.load(sys.stdin)" 2>/dev/null; then
        echo "[OK] $name 준비됨"
        return 0
      fi
    fi
    n=$((n + 1))
    echo "  $name 대기 중... ($n/$max)"
    sleep 2
  done
  echo "[실패] $name 이(가) ${max}초 내에 응답하지 않음. docker-compose up 후 잠시 기다렸다가 다시 실행하세요."
  exit 1
}

echo "=== 서비스 준비 대기 ==="
wait_for_ready "$PRODUCT_URL/products" "product-service"
echo ""

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
    curl -s -H "Authorization: Bearer $TOKEN" "$ORDER_URL/orders/$ORDER_ID" | python3 -m json.tool
    echo ""
    echo "=== 6. 당일 매출 집계 (settlement-service, RabbitMQ 이벤트 반영) ==="
    TODAY=$(date +%Y-%m-%d)
    SETTLE=$(curl -s "$SETTLEMENT_URL/settlements/daily?date=$TODAY" 2>/dev/null || true)
    if [ -n "$SETTLE" ] && echo "$SETTLE" | python3 -c "import sys,json; json.load(sys.stdin)" 2>/dev/null; then
      echo "$SETTLE" | python3 -m json.tool
    else
      echo "(settlement-service 미기동 또는 당일 데이터 없음)"
    fi
  fi
else
  echo "주문 실패 (HTTP $HTTP_CODE). 재고 부족/결제 실패 등 확인."
fi
