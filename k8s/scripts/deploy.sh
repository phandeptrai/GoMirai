#!/bin/bash
set -e

# Microservices list to monitor
CORE_SERVICES=("api-gateway" "ride-service" "identity-service")
NAMESPACE="gomirai"

echo "🚀 [STAGE 1] Applying Infrastructure and ConfigMaps..."
kubectl apply -f k8s/configmap.yaml -n $NAMESPACE
kubectl apply -f k8s/infrastructure.yaml -n $NAMESPACE
kubectl apply -f k8s/mongodb.yaml -n $NAMESPACE

echo "🚀 [STAGE 2] Deploying Microservices..."
kubectl apply -f k8s/services/ -n $NAMESPACE

echo "⌛ [STAGE 3] Waiting for Rollout to complete..."
for service in "${CORE_SERVICES[@]}"; do
    echo "Checking status for $service..."
    kubectl rollout status deployment/$service -n $NAMESPACE --timeout=300s
done

echo "✅ Deployment finished. System is up."
