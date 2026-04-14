# Chay tu thu muc repo:  .\k8s\deploy-gke.ps1
# 1) build-and-push: mvn + docker push Artifact Registry
# 2) deploy-windows: kubectl apply + secret tu .env
#
# GCP ~24 vCPU: cluster max 3 x e2-standard-8; pod limits (HPA max) ~21-22 vCPU.
# Disk: zonal StorageClass + small PVC + 30Gi boot/node; see k8s/storageclass-zonal-balanced.yaml.
# Uu tien ride-service (booking); HPA CPU threshold thap hon de de quan sat scale.
$ErrorActionPreference = "Stop"

# Thu muc goc = cha cua thu muc k8s
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $repoRoot) { $repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path) }

Set-Location $repoRoot

& "$PSScriptRoot\build-and-push.ps1"
& "$PSScriptRoot\deploy-windows.ps1"

Write-Host ""
Write-Host "[Goi y] Quan sat HPA khi gay tai (api-gateway -> ride-service):" -ForegroundColor Cyan
Write-Host "  kubectl get hpa -n gomirai -w" -ForegroundColor Gray
Write-Host "  kubectl get pods -n gomirai -l app=ride-service -w" -ForegroundColor Gray
Write-Host "Neu cluster cu dung --max-nodes 6: cap nhat pool xuong toi da 3 node e2-standard-8.`n" -ForegroundColor Yellow
