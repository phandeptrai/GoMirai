#!/bin/bash
set -e

echo -e "\033[1;36m==========================================================\033[0m"
echo -e "\033[1;36m🚀 GoMirai - Full Deployment Script (Ubuntu/Linux)\033[0m"
echo -e "\033[1;36m==========================================================\033[0m"

PROJECT_ID="gomirai-prod"
CLUSTER_NAME="gomirai-cluster"
ZONE="asia-northeast1-a"

echo -e "\n\033[1;33m[PHASE 1] TẠO CLUSTER (SETUP NODE)\033[0m"
read -p "Bạn có muốn tạo mới GKE Cluster không? (y/n): " createCluster
if [ "$createCluster" = "y" ]; then
    echo -e "\033[1;35m-> Đang tạo cluster (có thể mất 3-5 phút)...\033[0m"
    gcloud container clusters create $CLUSTER_NAME \
      --zone $ZONE \
      --num-nodes 3 \
      --machine-type e2-standard-4 \
      --disk-size 50 \
      --enable-autoscaling \
      --min-nodes 3 \
      --max-nodes 5 \
      --enable-autorepair \
      --enable-autoupgrade \
      --project $PROJECT_ID
fi

echo -e "\n\033[1;35m-> Kết nối kubectl...\033[0m"
gcloud container clusters get-credentials $CLUSTER_NAME --zone $ZONE --project $PROJECT_ID

echo "-> Kiểm tra nodes:"
kubectl get nodes

echo -e "\n\033[1;33m[PHASE 2] DEPLOY HỆ THỐNG\033[0m"

echo -e "\033[1;35m-> Bước 1: Tạo namespace\033[0m"
kubectl apply -f k8s/namespace.yaml

echo -e "\033[1;35m-> Bước 2: Tạo secret (từ .env)\033[0m"
if [ -f "k8s/create-secret.sh" ]; then
    chmod +x k8s/create-secret.sh
    ./k8s/create-secret.sh
else
    echo -e "\033[1;31m⚠ Không tìm thấy file k8s/create-secret.sh\033[0m"
fi

echo -e "\033[1;35m-> Bước 3: Deploy infrastructure (Consul, Zookeeper, Kafka)\033[0m"
kubectl apply -f k8s/infrastructure.yaml
echo "Đang chờ Infrastructure khởi động (có thể tốn vài phút)..."
kubectl wait --for=condition=ready pod -l app=consul -n gomirai --timeout=300s || true
kubectl wait --for=condition=ready pod -l app=zookeeper -n gomirai --timeout=300s || true
kubectl wait --for=condition=ready pod -l app=kafka -n gomirai --timeout=300s || true

echo -e "\033[1;35m-> Bước 4: Deploy ConfigMap\033[0m"
kubectl apply -f k8s/configmap.yaml

echo -e "\033[1;35m-> Bước 5: Deploy Microservices & API Gateway\033[0m"
kubectl apply -f k8s/services/
echo -e "\033[1;32m√ Hệ thống đang được cập nhật...\033[0m"

echo -e "\033[1;35m-> Bước 7: Deploy Ingress (Nginx LoadBalancer)\033[0m"
kubectl apply -f k8s/nginx-ingress.yaml

echo -e "\n\033[1;32m🎉 DEPLOY HOÀN TẤT!\033[0m"
echo -e "\033[1;36mGõ 'kubectl get svc nginx-service -n gomirai -w' để lấy địa chỉ EXTERNAL-IP của hệ thống.\033[0m"
