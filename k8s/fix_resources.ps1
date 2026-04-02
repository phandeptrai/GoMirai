Get-ChildItem "c:\Users\bbqdd\GoMirai\k8s\services\*.yaml" | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    $content = $content -replace 'cpu: "500m"', 'cpu: "200m"'
    $content = $content -replace 'memory: "768Mi"', 'memory: "512Mi"'
    Set-Content -Path $_.FullName -Value $content -NoNewline
}
