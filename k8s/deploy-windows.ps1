$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "🚀 GoMirai - Full Deployment Script (Windows)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$PROJECT_ID = "gomirai-prod"
$CLUSTER_NAME = "gomirai-cluster"
$ZONE = "asia-northeast1-a"

Write-Host "`n[PHASE 1] TAO CLUSTER (SETUP NODE)" -ForegroundColor Yellow
$createCluster = Read-Host "Ban co muon tao moi GKE Cluster khong? (y/n)"
if ($createCluster -eq 'y') {
    Write-Host "-> Dang tao cluster (co the mat 3-5 phut)..." -ForegroundColor Magenta
    gcloud container clusters create $CLUSTER_NAME `
      --zone $ZONE `
      --num-nodes 3 `
      --machine-type e2-standard-4 `
      --disk-size 50 `
      --enable-autoscaling `
      --min-nodes 3 `
      --max-nodes 5 `
      --enable-autorepair `
      --enable-autoupgrade `
      --project $PROJECT_ID
}

Write-Host "`n-> Ket noi kubectl..." -ForegroundColor Magenta
gcloud container clusters get-credentials $CLUSTER_NAME --zone $ZONE --project $PROJECT_ID

Write-Host "`n-> Kiem tra nodes:"
kubectl get nodes

Write-Host "`n[PHASE 2] DEPLOY HE THONG" -ForegroundColor Yellow

Write-Host "-> Buoc 1: Tao namespace" -ForegroundColor Magenta
kubectl apply -f k8s/namespace.yaml

Write-Host "-> Buoc 2: Tao secret (tu .env)" -ForegroundColor Magenta
if (Test-Path "k8s\create-secret.ps1") {
    .\k8s\create-secret.ps1
} else {
    Write-Host "⚠ Khong tim thay file k8s\create-secret.ps1" -ForegroundColor Red
}

Write-Host "-> Buoc 3: Deploy infrastructure (Consul, Zookeeper, Kafka, Redis)" -ForegroundColor Magenta
kubectl apply -f k8s/infrastructure.yaml
Write-Host "Dang cho Infrastructure khoi dong (co the ton vai phut)..."
kubectl wait --for=condition=ready pod -l app=consul -n gomirai --timeout=300s
kubectl wait --for=condition=ready pod -l app=zookeeper -n gomirai --timeout=300s
kubectl wait --for=condition=ready pod -l app=kafka -n gomirai --timeout=300s
kubectl wait --for=condition=ready pod -l app=redis -n gomirai --timeout=120s

Write-Host "-> Buoc 4: Deploy ConfigMap" -ForegroundColor Magenta
kubectl apply -f k8s/configmap.yaml

Write-Host "-> Buoc 5: Deploy Microservices & API Gateway" -ForegroundColor Magenta
kubectl apply -f k8s/services/
Write-Host "[OK] Da apply services." -ForegroundColor Green

Write-Host "-> Buoc 7: Deploy Ingress (Nginx LoadBalancer)" -ForegroundColor Magenta
kubectl apply -f k8s/nginx-ingress.yaml

Write-Host "`n🎉 DEPLOY HOAN TAT!" -ForegroundColor Green
Write-Host "Go 'kubectl get svc nginx-service -n gomirai -w' de lay dia chi EXTERNAL-IP cua he thong." -ForegroundColor Cyan
