$services = @("api-gateway.yaml", "auth-service.yaml", "tracking-service.yaml", "user-service.yaml")
foreach ($file in $services) {
    $filePath = "c:\Users\bbqdd\GoMirai\k8s\services\$file"
    if (Test-Path $filePath) {
        $content = Get-Content $filePath -Raw
        $content = $content -replace 'cpu: "200m"', 'cpu: "300m"'
        Set-Content -Path $filePath -Value $content -NoNewline
        Write-Host "Upgraded $file to 300m CPU"
    }
}
