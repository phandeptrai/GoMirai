$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "🚀 GoMirai - Autonomous Deployment Script (Full Auto)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$PROJECT_ID = "gomirai-prod"
$CLUSTER_NAME = "gomirai-cluster"
$ZONE = "asia-northeast1-a"

Write-Host "`n[PHASE 1] KIEM TRA & SETUP CLUSTER" -ForegroundColor Yellow

# Kiem tra Cluster ton tai
Write-Host "-> Dang kiem tra cluster '$CLUSTER_NAME' tren GKE..." -ForegroundColor Magenta
$clusterCheck = gcloud container clusters list --filter="name:$CLUSTER_NAME" --format="value(name)" --project $PROJECT_ID

if (-not $clusterCheck) {
    Write-Host "[-] Cluster khong ton tai. Dang tien hanh tao moi (mat 3-5 phut)..." -ForegroundColor Yellow
    gcloud container clusters create $CLUSTER_NAME `
      --zone $ZONE `
      --num-nodes 2 `
      --machine-type n2-standard-8 `
      --disk-size 35 `
      --enable-autoscaling `
      --min-nodes 2 `
      --max-nodes 3 `
      --enable-ip-alias `
      --enable-autorepair `
      --enable-autoupgrade `
      --project $PROJECT_ID
    Write-Host "[OK] Da tao xong Cluster." -ForegroundColor Green
} else {
    Write-Host "[+] Cluster da san sang." -ForegroundColor Green
}

Write-Host "`n-> Ket noi kubectl..." -ForegroundColor Magenta
gcloud container clusters get-credentials $CLUSTER_NAME --zone $ZONE --project $PROJECT_ID

Write-Host "`n[PHASE 2] DEPLOY HE THONG" -ForegroundColor Yellow

Write-Host "-> Buoc 1: Tao namespace & StorageClass" -ForegroundColor Magenta
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/storageclass-zonal-balanced.yaml

Write-Host "-> Buoc 2: Tao secret (tu .env)" -ForegroundColor Magenta
if (Test-Path "k8s\create-secret.ps1") {
    .\k8s\create-secret.ps1
}

Write-Host "-> Buoc 3: Deploy infrastructure (Consul, Kafka, Redis, MongoDB)" -ForegroundColor Magenta
kubectl apply -f k8s/infrastructure.yaml
kubectl apply -f k8s/mongodb.yaml

# Ham cho Pod san sang
function Wait-For-Pods($label) {
    Write-Host "⌛ Dang cho $label ready..." -NoNewline
    while ($true) {
        $status = kubectl get pods -l app=$label -n gomirai -o jsonpath='{.items[0].status.containerStatuses[0].ready}' 2>$null
        if ($status -eq "true") {
            Write-Host " [OK]" -ForegroundColor Green
            break
        }
        Write-Host "." -NoNewline
        Start-Sleep -Seconds 5
    }
}

Wait-For-Pods "consul"
Wait-For-Pods "kafka"
Wait-For-Pods "redis"
Wait-For-Pods "mongodb"

Write-Host "`n-> Buoc 4: Kich hoat hieu chinh MongoDB ReplicaSet (QUAN TRONG)" -ForegroundColor Yellow
$FQDN = "mongodb-0.mongodb-headless.gomirai.svc.cluster.local:27017"
$MONGO_INIT = "try { rs.initiate({_id: 'rs0', members: [{_id: 0, host: '$FQDN'}]}) } catch (e) { cfg = rs.conf(); cfg.members[0].host = '$FQDN'; rs.reconfig(cfg, {force: true}); }"
kubectl exec mongodb-0 -n gomirai -- mongosh --eval "$MONGO_INIT" 2>$null
Write-Host "[OK] Da thiet lap MongoDB voi FQDN: $FQDN" -ForegroundColor Green
Start-Sleep -Seconds 10 # Cho MongoDB bau Primary

Write-Host "`n-> Buoc 5: Deploy ConfigMap & Microservices" -ForegroundColor Magenta
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/services/

Write-Host "`n-> Buoc 6: Deploy Ingress" -ForegroundColor Magenta
kubectl apply -f k8s/nginx-ingress.yaml

Write-Host "`n🎉 DEPLOY HOAN TAT!" -ForegroundColor Green
Write-Host "Go 'kubectl get svc nginx-service -n gomirai -w' de lay dia chi EXTERNAL-IP." -ForegroundColor Cyan
