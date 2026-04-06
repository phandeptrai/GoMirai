# =============================================================
# deploy-perf-changes.ps1
# Deploy CHỈ các services đã thay đổi trong lần cải tiến hiệu suất:
#   - ApiGateway   (connection pool, circuit breaker, timeout tuning)
#   - BookingService (2dsphere geo-index, $nearSphere query)
#   - PaymentService (@Version optimistic locking)
# Cộng với apply K8s YAML cho api-gateway và tracking-service
# =============================================================

$ErrorActionPreference = "Stop"

$PROJECT_ID    = "gomirai-prod"
$REGION        = "asia-southeast1"
$REPO_NAME     = "gomirai-repo"
$IMAGE_PREFIX  = "$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME"
$CLUSTER_NAME  = "gomirai-cluster"
$ZONE          = "asia-northeast1-a"
$NAMESPACE     = "gomirai"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " GoMirai - Deploy Performance Improvements" -ForegroundColor Cyan
Write-Host " Chỉ build: ApiGateway, BookingService, PaymentService" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# ──────────────────────────────────────────────────────────────
# STEP 0: Kết nối GKE cluster
# ──────────────────────────────────────────────────────────────
Write-Host "`n[0] Kết nối kubectl tới GKE cluster..." -ForegroundColor Yellow
gcloud container clusters get-credentials $CLUSTER_NAME --zone $ZONE --project $PROJECT_ID
Write-Host "-> OK" -ForegroundColor Green

# ──────────────────────────────────────────────────────────────
# STEP 1: Install gomirai-common-lib vào Maven local cache
# ──────────────────────────────────────────────────────────────
Write-Host "`n[1] Install gomirai-common-lib vào local Maven repo..." -ForegroundColor Yellow
$commonLibPath = Join-Path $repoRoot "gomirai-common-lib"
$mvnwPath = Join-Path $repoRoot "AuthService\mvnw.cmd"

Push-Location $commonLibPath
try {
    if (Get-Command mvn -ErrorAction SilentlyContinue) {
        mvn install -DskipTests -q
    } elseif (Test-Path $mvnwPath) {
        Write-Host "-> Using mvnw from AuthService..." -ForegroundColor DarkGray
        & $mvnwPath "-f" "pom.xml" install -DskipTests -q
    } else {
        throw "Cannot find 'mvn' or 'mvnw.cmd' in AuthService folder."
    }
    if ($LASTEXITCODE -ne 0) { throw "common-lib install failed" }
    Write-Host "-> gomirai-common-lib installed OK" -ForegroundColor Green
} finally {
    Pop-Location
}

# ──────────────────────────────────────────────────────────────
# STEP 2: Build + Push chỉ 3 services đã thay đổi
# ──────────────────────────────────────────────────────────────
$CHANGED_SERVICES = @(
    @{ folder = "ApiGateway";      image = "api-gateway"      },
    @{ folder = "AuthService";  image = "auth-service"  },
    @{ folder = "DriverService";  image = "driver-service"  },
    @{ folder = "TrackingService";  image = "tracking-service"  }
)

Write-Host "`n[2] Configure Docker auth..." -ForegroundColor Yellow
gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet
Write-Host "-> Docker auth OK" -ForegroundColor Green

foreach ($svc in $CHANGED_SERVICES) {
    $folder   = $svc.folder
    $image    = $svc.image
    $imageTag = "${IMAGE_PREFIX}/${image}:latest"
    $svcPath  = Join-Path $repoRoot $folder

    Write-Host ""
    Write-Host "---------------------------------------------------" -ForegroundColor Cyan
    Write-Host "  BUILD & PUSH: $folder -> $imageTag" -ForegroundColor Cyan
    Write-Host "---------------------------------------------------" -ForegroundColor Cyan

    Push-Location $svcPath
    try {
        # 2a. Maven build
        Write-Host "-> Maven build..." -ForegroundColor Magenta
        if (Get-Command mvn -ErrorAction SilentlyContinue) {
            mvn clean package -DskipTests
        } elseif (Test-Path $mvnwPath) {
            & $mvnwPath clean package -DskipTests
        } else {
            throw "Maven build failed - no mvn or mvnw found!"
        }
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed for $folder" }

        # 2b. Docker build (Dockerfile context MUST be repo root for common-lib)
        Write-Host "-> Docker build $imageTag..." -ForegroundColor Magenta
        $dockerfilePath = Join-Path $folder "Dockerfile"
        Push-Location $repoRoot
        try {
            docker build -t $imageTag -f $dockerfilePath .
        } finally {
            Pop-Location
        }
        if ($LASTEXITCODE -ne 0) { throw "Docker build failed for $folder" }

        # 2c. Docker push
        Write-Host "-> Docker push $imageTag..." -ForegroundColor Magenta
        docker push $imageTag
        if ($LASTEXITCODE -ne 0) { throw "Docker push failed for $folder" }

        Write-Host "-> $folder DONE!" -ForegroundColor Green
    } finally {
        Pop-Location
    }
}

# ──────────────────────────────────────────────────────────────
# STEP 3: Apply K8s YAML đã cập nhật resource limits
# ──────────────────────────────────────────────────────────────
Write-Host "`n[3] Apply K8s YAML đã cập nhật..." -ForegroundColor Yellow
kubectl apply -f k8s/services/api-gateway.yaml
kubectl apply -f k8s/services/auth-service.yaml
kubectl apply -f k8s/services/user-service.yaml
kubectl apply -f k8s/services/booking-service.yaml
kubectl apply -f k8s/services/payment-service.yaml
kubectl apply -f k8s/services/tracking-service.yaml
kubectl apply -f k8s/services/driver-service.yaml
Write-Host "-> YAML applied OK" -ForegroundColor Green

# ──────────────────────────────────────────────────────────────
# STEP 4: Trigger rolling restart để pull image :latest mới
# ──────────────────────────────────────────────────────────────
Write-Host "`n[4] Trigger rolling restart cho 3 services đã thay đổi..." -ForegroundColor Yellow
kubectl rollout restart deployment/api-gateway     -n $NAMESPACE
kubectl rollout restart deployment/auth-service    -n $NAMESPACE
kubectl rollout restart deployment/driver-service    -n $NAMESPACE
kubectl rollout restart deployment/booking-service -n $NAMESPACE
kubectl rollout restart deployment/tracking-service -n $NAMESPACE

Write-Host "-> Rollout restart issued OK" -ForegroundColor Green

# ──────────────────────────────────────────────────────────────
# STEP 5: Chờ rollout hoàn tất
# ──────────────────────────────────────────────────────────────
Write-Host "`n[5] Chờ rollout hoàn tất (timeout 5 phút mỗi service)..." -ForegroundColor Yellow

foreach ($dep in @("api-gateway", "auth-service", "user-service", "booking-service", "payment-service")) {
    Write-Host "   Waiting: $dep..." -ForegroundColor DarkGray
    kubectl rollout status deployment/$dep -n $NAMESPACE --timeout=300s
    if ($LASTEXITCODE -ne 0) {
        Write-Host "   WARN: $dep rollout timed out hoac loi, kiem tra pods:" -ForegroundColor Red
        kubectl get pods -n $NAMESPACE -l app=$dep
    } else {
        Write-Host "   OK: $dep is READY" -ForegroundColor Green
    }
}

# ──────────────────────────────────────────────────────────────
# STEP 6: Hiển thị trạng thái cuối
# ──────────────────────────────────────────────────────────────
Write-Host "`n[6] Trạng thái pods sau deploy:" -ForegroundColor Yellow
kubectl get pods -n $NAMESPACE -l "app in (api-gateway,auth-service,user-service,booking-service,payment-service,tracking-service)"

Write-Host "`n=========================================================="
Write-Host " DEPLOY HOÀN TẤT!" -ForegroundColor Green
Write-Host " Chạy k6 test để kiểm tra kết quả:" -ForegroundColor Cyan
Write-Host "   k6 run -e BASE_URL=http://34.85.39.216 k6/SmokeTest/main.js" -ForegroundColor Cyan
Write-Host "=========================================================="
