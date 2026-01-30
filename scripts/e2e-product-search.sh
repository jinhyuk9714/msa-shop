#!/usr/bin/env bash
# E2E: 상품 검색 API (GET /products?name=...&minPrice=...&maxPrice=...)
# product-service 또는 GATEWAY_URL(8080) 경유로 실행.
# K8s 사용 시: kubectl port-forward svc/msa-shop-api-gateway 8080:8080 후 GATEWAY_URL=http://localhost:8080 실행.
set -e

if [ -n "$GATEWAY_URL" ]; then
  PRODUCT_URL="$GATEWAY_URL"
else
  PRODUCT_URL="${PRODUCT_URL:-http://localhost:8082}"
fi

wait_for_ready() {
  local url="$1"
  local max=10 n=0
  while [ $n -lt $max ]; do
    if curl -sf "$url" >/dev/null 2>&1; then
      echo "[OK] product-service 준비됨"
      return 0
    fi
    n=$((n + 1))
    echo "  대기 중... ($n/$max)"
    sleep 2
  done
  echo "[실패] product-service 응답 없음"
  if [ -n "$GATEWAY_URL" ]; then
    echo "  K8s 사용 시: 다른 터미널에서 kubectl port-forward svc/msa-shop-api-gateway 8080:8080 실행 후 다시 시도."
  fi
  exit 1
}

echo "=== 서비스 준비 대기 ==="
wait_for_ready "$PRODUCT_URL/products"
echo ""

echo "=== 1. 전체 목록 GET /products ==="
FULL=$(curl -s "$PRODUCT_URL/products")
echo "$FULL" | python3 -m json.tool
COUNT_FULL=$(echo "$FULL" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))")
echo "총 $COUNT_FULL 건"
echo ""

echo "=== 2. 이름 검색 GET /products?name=상품 ==="
NAME_RESULT=$(curl -s "$PRODUCT_URL/products?name=%EC%83%81%ED%92%88")
echo "$NAME_RESULT" | python3 -m json.tool
COUNT_NAME=$(echo "$NAME_RESULT" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))")
echo "결과 $COUNT_NAME 건 (이름에 '상품' 포함)"
echo ""

echo "=== 3. 가격 범위 GET /products?minPrice=5000&maxPrice=15000 ==="
PRICE_RESULT=$(curl -s "$PRODUCT_URL/products?minPrice=5000&maxPrice=15000")
echo "$PRICE_RESULT" | python3 -m json.tool
COUNT_PRICE=$(echo "$PRICE_RESULT" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))")
echo "결과 $COUNT_PRICE 건 (5000~15000원)"
echo ""

echo "=== 4. 이름+가격 GET /products?name=테스트&minPrice=10000 ==="
BOTH=$(curl -s "$PRODUCT_URL/products?name=%ED%85%8C%EC%8A%A4%ED%8A%B8&minPrice=10000")
echo "$BOTH" | python3 -m json.tool
COUNT_BOTH=$(echo "$BOTH" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))")
echo "결과 $COUNT_BOTH 건"
echo ""

echo "=== 5. 검색 없음 GET /products?name=없는상품 ==="
EMPTY=$(curl -s "$PRODUCT_URL/products?name=%EC%97%86%EB%8A%94%EC%83%81%ED%92%88")
echo "$EMPTY" | python3 -m json.tool
COUNT_EMPTY=$(echo "$EMPTY" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))")
echo "결과 $COUNT_EMPTY 건 (기대: 0)"
echo ""

echo "=== 요약 ==="
echo "전체: $COUNT_FULL, 이름(상품): $COUNT_NAME, 가격(5k~15k): $COUNT_PRICE, 이름+가격: $COUNT_BOTH, 없음: $COUNT_EMPTY"
if [ "$COUNT_FULL" -ge 1 ] && [ "$COUNT_NAME" -ge 1 ] && [ "$COUNT_EMPTY" -eq 0 ]; then
  echo "[OK] 상품 검색 시나리오 통과"
else
  echo "[참고] 시딩 데이터(테스트 상품 A/B/C) 기준으로 결과 확인"
fi
