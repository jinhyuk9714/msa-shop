#!/usr/bin/env bash
# E2E 실패 시나리오: 재고 부족(409), (선택) 결제 실패 등.
# 사전: 회원가입·로그인 완료. GATEWAY_URL 설정 시 Gateway 경유.
set -e

if [ -n "$GATEWAY_URL" ]; then
  USER_URL="$GATEWAY_URL"
  ORDER_URL="$GATEWAY_URL"
else
  USER_URL="${USER_URL:-http://localhost:8081}"
  ORDER_URL="${ORDER_URL:-http://localhost:8083}"
fi

echo "=== 로그인 (실패 시나리오용 토큰) ==="
LOGIN_RESP=$(curl -s -X POST "$USER_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test1234"}')
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null || true)
if [ -z "$TOKEN" ]; then
  echo "로그인 실패. 먼저 e2e-flow.sh 로 회원가입·로그인 후 실행하세요."
  exit 1
fi
echo "Token 획득됨"
echo ""

echo "=== 1. 재고 부족 (상품 3, 수량 10 → 409 CONFLICT) ==="
echo "상품 3은 재고 5개. quantity=10 요청 시 재고 부족."
RESP=$(curl -s -w "\n%{http_code}" -X POST "$ORDER_URL/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":3,"quantity":10,"paymentMethod":"CARD"}')
BODY=$(echo "$RESP" | sed '$d')
CODE=$(echo "$RESP" | tail -n 1)
echo "HTTP $CODE"
echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
if [ "$CODE" = "409" ]; then
  echo "[OK] 예상대로 409 CONFLICT (재고 부족)"
else
  echo "[참고] 409가 아닌 경우: 상품 3 재고가 10 이상이면 201이 나올 수 있음."
fi
echo ""

echo "=== 2. 주문 취소 성공 (PAID → CANCELLED) ==="
echo "주문 생성 후 바로 취소."
ORDER_RESP=$(curl -s -w "\n%{http_code}" -X POST "$ORDER_URL/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"productId":1,"quantity":1,"paymentMethod":"CARD"}')
ORDER_BODY=$(echo "$ORDER_RESP" | sed '$d')
ORDER_CODE=$(echo "$ORDER_RESP" | tail -n 1)
ORDER_ID=$(echo "$ORDER_BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || true)
if [ "$ORDER_CODE" = "201" ] && [ -n "$ORDER_ID" ]; then
  CANCEL_RESP=$(curl -s -w "\n%{http_code}" -X PATCH "$ORDER_URL/orders/$ORDER_ID/cancel" \
    -H "Authorization: Bearer $TOKEN")
  CANCEL_CODE=$(echo "$CANCEL_RESP" | tail -n 1)
  echo "PATCH /orders/$ORDER_ID/cancel → HTTP $CANCEL_CODE"
  if [ "$CANCEL_CODE" = "200" ]; then
    echo "[OK] 주문 취소 성공"
  else
    echo "[참고] 취소 실패 (기존 주문에 paymentId 없으면 409)"
  fi
else
  echo "[스킵] 주문 생성 실패로 취소 테스트 생략"
fi
echo ""

echo "=== 3. 이미 취소된 주문 재취소 (409) ==="
if [ -n "$ORDER_ID" ]; then
  CANCEL2=$(curl -s -w "\n%{http_code}" -X PATCH "$ORDER_URL/orders/$ORDER_ID/cancel" \
    -H "Authorization: Bearer $TOKEN")
  CODE2=$(echo "$CANCEL2" | tail -n 1)
  echo "동일 주문 재취소 → HTTP $CODE2"
  if [ "$CODE2" = "409" ]; then
    echo "[OK] 예상대로 409 CONFLICT (이미 취소됨)"
  fi
else
  echo "[스킵] 위에서 주문 ID 없음"
fi
echo ""

echo "=== 완료 ==="
echo "추가 시나리오: 결제 실패(402)는 payment-service 규칙(amount 등)으로 유도 가능."
echo "문서: docs/FAILURE-SCENARIOS.md"
