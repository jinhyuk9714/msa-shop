#!/usr/bin/env bash
# 프로젝트 루트에서 실행. 6개 서비스 Docker 이미지를 msa-shop-*:latest 로 빌드.
# Docker Desktop K8s: 빌드 후 kubectl apply -f k8s/ 하면 로컬 이미지 사용.
# Minikube: eval $(minikube docker-env) 후 이 스크립트 실행.
set -e
cd "$(dirname "$0")/.."

echo "Building api-gateway..."
docker build -t msa-shop-api-gateway:latest -f api-gateway/Dockerfile .
echo "Building user-service..."
docker build -t msa-shop-user-service:latest -f user-service/Dockerfile .
echo "Building product-service..."
docker build -t msa-shop-product-service:latest -f product-service/Dockerfile .
echo "Building order-service..."
docker build -t msa-shop-order-service:latest -f order-service/Dockerfile .
echo "Building payment-service..."
docker build -t msa-shop-payment-service:latest -f payment-service/Dockerfile .
echo "Building settlement-service..."
docker build -t msa-shop-settlement-service:latest -f settlement-service/Dockerfile .
echo "Done. Images: msa-shop-*:latest"
docker images msa-shop-*
