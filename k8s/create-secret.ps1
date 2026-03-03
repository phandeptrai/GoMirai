# =============================================================================
# GoMirai - Tao K8s Secret tu .env (Version Sửa Lỗi)
# =============================================================================
$NAMESPACE = "gomirai"
$SCRIPT_PATH = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ENV_FILE = Join-Path $SCRIPT_PATH "..\.env"

if (-not (Test-Path $ENV_FILE)) {
    Write-Host "[-] Khong tim thay file .env tai: $ENV_FILE" -ForegroundColor Red
    exit 1
}

Write-Host "[+] Dang doc .env tu: $ENV_FILE" -ForegroundColor Cyan

$envVars = @{}
Get-Content $ENV_FILE | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $idx = $line.IndexOf('=')
        if ($idx -gt 0) {
            $key   = $line.Substring(0, $idx).Trim()
            $value = $line.Substring($idx + 1).Trim().Trim('"').Trim("'")
            $envVars[$key] = $value
        }
    }
}

$secrets = @{}
$secrets["SECURITY_JWT_SECRET"]     = $envVars["JWT_SECRET"]
$secrets["AUTH_MONGODB_URI"]        = $envVars["AUTH_MONGODB_URI"]
$secrets["USER_MONGODB_URI"]        = $envVars["USER_MONGODB_URI"]
$secrets["DRIVER_MONGODB_URI"]      = $envVars["DRIVER_MONGODB_URI"]
$secrets["PRICING_MONGODB_URI"]     = $envVars["PRICING_MONGODB_URI"]
$secrets["PAYMENT_MONGODB_URI"]     = $envVars["PAYMENT_MONGODB_URI"]
$secrets["BOOKING_MONGODB_URI"]     = $envVars["BOOKING_MONGODB_URI"]
$secrets["NOTIFICATION_MONGODB_URI"]= $envVars["NOTIFICATION_MONGODB_URI"]
$secrets["REVIEW_MONGODB_URI"]      = $envVars["REVIEW_MONGODB_URI"]
$secrets["SPRING_REDIS_URL"]        = $envVars["SPRING_REDIS_URL"]

if ($envVars["SPRING_REDIS_URL"] -match "redis://([^:]+):([^@]+)@([^:]+):(\d+)") {
    $secrets["SPRING_REDIS_USERNAME"] = $matches[1]
    $secrets["SPRING_REDIS_PASSWORD"] = $matches[2]
    $secrets["SPRING_REDIS_HOST"]     = $matches[3]
    $secrets["SPRING_REDIS_PORT"]     = $matches[4]
}
$secrets["SPRING_REDIS_SSL_ENABLED"] = "false"
$secrets["GOOGLE_CLIENT_ID"]         = $envVars["GOOGLE_CLIENT_ID"]
$secrets["GOOGLE_CLIENT_SECRET"]     = $envVars["GOOGLE_CLIENT_SECRET"]
$secrets["VNPAY_TMN_CODE"]           = $envVars["VNPAY_TMN_CODE"]
$secrets["VNPAY_HASH_SECRET"]        = $envVars["VNPAY_HASH_SECRET"]
$secrets["VNPAY_PAYMENT_URL"]        = $envVars["VNPAY_PAYMENT_URL"]
$secrets["VNPAY_RETURN_URL"]         = $envVars["VNPAY_RETURN_URL"]
$secrets["MAPBOX_ACCESS_TOKEN"]      = $envVars["MAPBOX_ACCESS_TOKEN"]

Write-Host "[*] Dang tao secret 'gomirai-secrets'..." -ForegroundColor Cyan
kubectl delete secret gomirai-secrets -n $NAMESPACE --ignore-not-found

$kubeArgs = @("create", "secret", "generic", "gomirai-secrets", "--namespace=$NAMESPACE")
foreach ($entry in $secrets.GetEnumerator()) {
    if ($entry.Value) {
        $kubeArgs += "--from-literal=$($entry.Key)=$($entry.Value)"
    }
}

kubectl @kubeArgs

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n[OK] Secret gomirai-secrets da duoc tao!" -ForegroundColor Green
} else {
    Write-Host "`n[LOI] Khong the tao secret. Kiem tra ket noi GKE hoac Namespace." -ForegroundColor Red
}