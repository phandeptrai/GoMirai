# Chạy từ thư mục k8s:  .\k8s\deploy-gke.ps1
# 1) build-and-push: mvn install common-lib + build/push mọi image lên Artifact Registry
# 2) deploy-windows: kubectl apply + secret từ .env (hỏi có tạo cluster mới không)
$ErrorActionPreference = "Stop"

# Thư mục gốc là cha của thư mục k8s
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $repoRoot) { $repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path) }

Set-Location $repoRoot

# Chạy các script phụ trợ nằm cùng thư mục k8s/
& "$PSScriptRoot\build-and-push.ps1"
& "$PSScriptRoot\deploy-windows.ps1"

