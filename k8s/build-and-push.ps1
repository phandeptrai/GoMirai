# Run without ErrorActionPreference=Stop because gcloud writes progress to STDERR
$PROJECT_ID = "gomirai-prod"
$REGION = "asia-southeast1"
$REPO_NAME = "gomirai-repo"
$IMAGE_PREFIX = "$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "🚀 GoMirai Microservices - Build & Push to Artifact Registry" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Login to project
Write-Host "`n[1] Check GCP project..." -ForegroundColor Yellow
gcloud config set project $PROJECT_ID

# 2. Check/Create Artifact Repository
Write-Host "`n[2] Check Artifact Registry Repository ($REPO_NAME)..." -ForegroundColor Yellow
$repoExists = gcloud artifacts repositories list --location=$REGION --format="value(name)" | Select-String -Pattern $REPO_NAME
if (-not $repoExists) {
    Write-Host "-> Repository not found. Creating..."
    gcloud artifacts repositories create $REPO_NAME --repository-format=docker --location=$REGION --description="Docker repository for GoMirai Microservices"
} else {
    Write-Host "-> Repository exists."
}

# 3. Configure Docker
Write-Host "`n[3] Configure Docker for Artifact Registry..." -ForegroundColor Yellow
gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet

# 4. Service list
$SERVICES = @(
    "auth-service",
    "user-service",
    "payment-service",
    "driver-service",
    "tracking-service",
    "map-service",
    "pricing-service",
    "booking-service",
    "review-service",
    "notification-service",
    "api-gateway"
)

# 5. Build and Push
Write-Host "`n[4] Start Build and Push..." -ForegroundColor Yellow
$currentDir = Get-Location

foreach ($svc in $SERVICES) {
    # Convert to PascalCase. E.g., auth-service -> AuthService
    $folderName = ([System.Globalization.CultureInfo]::CurrentCulture.TextInfo.ToTitleCase($svc)).Replace("-", "")
    
    $folderPath = Join-Path $currentDir $folderName
    
    if (-not (Test-Path $folderPath)) {
        Write-Host "x Skip $svc (Folder not found: $folderName)" -ForegroundColor Red
        continue
    }

    Write-Host "`n---------------------------------------------------" -ForegroundColor Cyan
    Write-Host "⚙️ PROCESSING: $svc (Folder: $folderName)" -ForegroundColor Cyan
    Write-Host "---------------------------------------------------" -ForegroundColor Cyan
    
    Set-Location $folderPath

    try {
        # 5.1 Build JAR
        Write-Host "-> Building Java project (mvnw clean package)..." -ForegroundColor Magenta
        if (Test-Path "mvnw.cmd") {
            .\mvnw.cmd clean package -DskipTests
        } else {
            mvn clean package -DskipTests
        }

        if ($LASTEXITCODE -ne 0) { throw "Maven build failed!" }

        # 5.2 Build Docker Image
        $imageTag = "${IMAGE_PREFIX}/${svc}:latest"
        Write-Host "-> Building Docker Image: $imageTag..." -ForegroundColor Magenta
        docker build -t $imageTag -f Dockerfile ..

        # 5.3 Push Docker Image
        Write-Host "-> Pushing Image to Artifact Registry..." -ForegroundColor Magenta
        docker push $imageTag
        
        Write-Host "V Complete: $svc!" -ForegroundColor Green
    }
    catch {
        Write-Host "x ERROR processing $svc" -ForegroundColor Red
        Set-Location $currentDir
        throw $_
    }

    Set-Location $currentDir
}

Write-Host "`n🎉 BUILD & PUSH COMPLETED!" -ForegroundColor Green
Write-Host "Kubernetes will now re-pull the images." -ForegroundColor Yellow
