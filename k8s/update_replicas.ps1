$services = @{
    "api-gateway.yaml" = @{ min=2; max=6 }
    "auth-service.yaml" = @{ min=2; max=3 }
    "user-service.yaml" = @{ min=2; max=4 }
    "booking-service.yaml" = @{ min=2; max=5 }
    "driver-service.yaml" = @{ min=2; max=4 }
    "tracking-service.yaml" = @{ min=2; max=4 }
    "payment-service.yaml" = @{ min=1; max=3 }
    "notification-service.yaml" = @{ min=1; max=3 }
    "pricing-service.yaml" = @{ min=1; max=3 }
    "review-service.yaml" = @{ min=1; max=2 }
    "map-service.yaml" = @{ min=1; max=3 }
}

$path = "c:\Users\bbqdd\GoMirai\k8s\services\"

foreach ($item in $services.GetEnumerator()) {
    $file = $item.Key
    $filePath = Join-Path $path $file
    if (Test-Path $filePath) {
        $content = Get-Content $filePath -Raw
        $min = $item.Value.min
        $max = $item.Value.max
        
        # Update Deployment replicas
        # Match 'replicas: X' only inside the Deployment section
        # Use regex to match after 'kind: Deployment' but before the next '---'
        if ($content -match "(?s)(kind: Deployment.*?replicas: )\d+") {
             $content = [regex]::Replace($content, "(?s)(kind: Deployment.*?replicas: )\d+", "`${1}$min")
        }
        
        # Update HPA minReplicas
        $content = $content -replace '(minReplicas: )\d+', "`${1}$min"
        
        # Update HPA maxReplicas
        $content = $content -replace '(maxReplicas: )\d+', "`${1}$max"
        
        Set-Content -Path $filePath -Value $content -NoNewline
        Write-Host "Updated ${file}: Min=${min}, Max=${max}"
    }
}
