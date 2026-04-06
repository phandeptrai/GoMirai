$REPOS = "asia-southeast1-docker.pkg.dev/gomirai-prod/gomirai-repo"
$SERVICES = @("AuthService", "ApiGateway", "TrackingService", "DriverService")

$tag = Get-Date -Format "yyyyMMddHHmmss"

foreach ($service in $SERVICES) {
    Write-Host "`n📦 Processing: $service" -ForegroundColor Yellow
    Set-Location "$PSScriptRoot\..\$service"

    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Build failed" }

    $imageTag = "$REPOS/$($service.ToLower()):$tag"

    docker build -t $imageTag .
    docker push $imageTag

    # mapping tên k8s
    switch ($service) {
        "AuthService" { $k8sName = "auth-service" }
        "ApiGateway" { $k8sName = "api-gateway" }
        "TrackingService" { $k8sName = "tracking-service" }
        "DriverService" { $k8sName = "driver-service" }
    }

    # update image (QUAN TRỌNG)
    kubectl set image deployment/$k8sName `
        $k8sName=$imageTag -n gomirai

    # theo dõi rollout
    kubectl rollout status deployment/$k8sName -n gomirai
}

Set-Location "$PSScriptRoot"
Write-Host "`n✨ DONE!" -ForegroundColor Green