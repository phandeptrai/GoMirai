#!/usr/bin/env powershell
# =============================================================================
# GoMirai - Tạo K8s Secret ĐỌC THẲNG TỪ .env
# =============================================================================
# Cách dùng (chạy từ thư mục GoMirai/):
#   cd c:\Users\bbqdd\Documents\HocKy7\SOA\GoMirai
#   kubectl apply -f k8s/namespace.yaml
#   .\k8s\create-secret.ps1
# =============================================================================

$NAMESPACE = "gomirai"
# k8s/ nằm trong GoMirai/ → .env nằm ở GoMirai/.env (cùng cấp với k8s/)
$ENV_FILE  = "$PSScriptRoot\..\.env"

# ── 1. Kiểm tra file .env tồn tại ────────────────────────────────────────────
if (-not (Test-Path $ENV_FILE)) {
    Write-Host "❌ Không tìm thấy file .env tại: $ENV_FILE" -ForegroundColor Red
    exit 1
}

Write-Host "📄 Đọc .env từ: $ENV_FILE" -ForegroundColor Cyan

# ── 2. Parse .env → hashtable ────────────────────────────────────────────────
# Bỏ qua comment (#) và dòng trống
$envVars = @{}
Get-Content $ENV_FILE | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $idx = $line.IndexOf('=')
        if ($idx -gt 0) {
            $key   = $line.Substring(0, $idx).Trim()
            $value = $line.Substring($idx + 1).Trim()
            # Bỏ dấu ngoặc kép nếu có
            $value = $value.Trim('"').Trim("'")
            $envVars[$key] = $value
        }
    }
}

Write-Host "✅ Đọc được $($envVars.Count) biến từ .env" -ForegroundColor Green

# ── 3. Build bộ key cho K8s Secret ───────────────────────────────────────────
# Key trong Secret PHẢI khớp với application.properties
# Một số key trong .env khác tên so với application.properties → cần remap

$secrets = @{}

# JWT: .env dùng JWT_SECRET, app dùng SECURITY_JWT_SECRET
$secrets["SECURITY_JWT_SECRET"]      = $envVars["JWT_SECRET"]

# MongoDB per-service (key trùng tên → copy thẳng)
$secrets["AUTH_MONGODB_URI"]         = $envVars["AUTH_MONGODB_URI"]
$secrets["USER_MONGODB_URI"]         = $envVars["USER_MONGODB_URI"]
$secrets["DRIVER_MONGODB_URI"]       = $envVars["DRIVER_MONGODB_URI"]
$secrets["PRICING_MONGODB_URI"]      = $envVars["PRICING_MONGODB_URI"]
$secrets["PAYMENT_MONGODB_URI"]      = $envVars["PAYMENT_MONGODB_URI"]
$secrets["BOOKING_MONGODB_URI"]      = $envVars["BOOKING_MONGODB_URI"]
$secrets["NOTIFICATION_MONGODB_URI"] = $envVars["NOTIFICATION_MONGODB_URI"]
$secrets["REVIEW_MONGODB_URI"]       = $envVars["REVIEW_MONGODB_URI"]

# Redis URL (copy thẳng)
$secrets["SPRING_REDIS_URL"]         = $envVars["SPRING_REDIS_URL"]

# Parse thêm SPRING_REDIS_HOST / PORT / PASSWORD từ URL
# Format: redis://username:password@host:port
if ($envVars["SPRING_REDIS_URL"] -match "redis://([^:]+):([^@]+)@([^:]+):(\d+)") {
    $secrets["SPRING_REDIS_USERNAME"] = $matches[1]
    $secrets["SPRING_REDIS_PASSWORD"] = $matches[2]
    $secrets["SPRING_REDIS_HOST"]     = $matches[3]
    $secrets["SPRING_REDIS_PORT"]     = $matches[4]
    Write-Host "✅ Parse Redis URL thành công: host=$($matches[3]) port=$($matches[4])" -ForegroundColor Green
} else {
    Write-Host "⚠️  Không parse được Redis URL, set thủ công" -ForegroundColor Yellow
    $secrets["SPRING_REDIS_HOST"]     = ""
    $secrets["SPRING_REDIS_PORT"]     = "6379"
    $secrets["SPRING_REDIS_PASSWORD"] = ""
    $secrets["SPRING_REDIS_USERNAME"] = "default"
}
$secrets["SPRING_REDIS_SSL_ENABLED"] = "false"

# Google OAuth (copy thẳng)
$secrets["GOOGLE_CLIENT_ID"]         = $envVars["GOOGLE_CLIENT_ID"]
$secrets["GOOGLE_CLIENT_SECRET"]     = $envVars["GOOGLE_CLIENT_SECRET"]

# VNPay (copy thẳng)
$secrets["VNPAY_TMN_CODE"]           = $envVars["VNPAY_TMN_CODE"]
$secrets["VNPAY_HASH_SECRET"]        = $envVars["VNPAY_HASH_SECRET"]
$secrets["VNPAY_PAYMENT_URL"]        = $envVars["VNPAY_PAYMENT_URL"]
$secrets["VNPAY_RETURN_URL"]         = $envVars["VNPAY_RETURN_URL"]

# Mapbox (copy thẳng)
$secrets["MAPBOX_ACCESS_TOKEN"]      = $envVars["MAPBOX_ACCESS_TOKEN"]

# ── 4. Kiểm tra không có key nào bị null ─────────────────────────────────────
$missing = $secrets.GetEnumerator() | Where-Object { -not $_.Value }
if ($missing) {
    Write-Host ""
    Write-Host "⚠️  Các key sau bị trống trong .env:" -ForegroundColor Yellow
    $missing | ForEach-Object { Write-Host "   - $($_.Key)" -ForegroundColor Yellow }
    Write-Host ""
}

# ── 5. Build lệnh kubectl create secret ──────────────────────────────────────
Write-Host ""
Write-Host "🔐 Tạo secret 'gomirai-secrets' trong namespace '$NAMESPACE'..." -ForegroundColor Cyan

# Xóa secret cũ nếu có
kubectl delete secret gomirai-secrets -n $NAMESPACE --ignore-not-found | Out-Null

# Build argument list động từ hashtable
$args = @("create", "secret", "generic", "gomirai-secrets", "--namespace=$NAMESPACE")
foreach ($entry in $secrets.GetEnumerator()) {
    $args += "--from-literal=$($entry.Key)=$($entry.Value)"
}

kubectl @args

# ── 6. Kết quả ───────────────────────────────────────────────────────────────
if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✅ Secret tạo thành công!" -ForegroundColor Green
    Write-Host ""
    Write-Host "📋 Kiểm tra:" -ForegroundColor Yellow
    Write-Host "   kubectl get secret gomirai-secrets -n $NAMESPACE"
    Write-Host "   kubectl describe secret gomirai-secrets -n $NAMESPACE"
    Write-Host ""
    Write-Host "⚠️  Cập nhật VNPAY_RETURN_URL sau khi có External IP của nginx:" -ForegroundColor Yellow
    Write-Host '   kubectl patch secret gomirai-secrets -n gomirai --type=merge -p "{\"stringData\":{\"VNPAY_RETURN_URL\":\"http://YOUR-EXTERNAL-IP/payment/vnpay/result\"}}"'
} else {
    Write-Host ""
    Write-Host "❌ Tạo secret thất bại! Kiểm tra:" -ForegroundColor Red
    Write-Host "   - kubectl kết nối GKE chưa? chạy: gcloud container clusters get-credentials <cluster> --zone <zone>"
    Write-Host "   - Namespace tồn tại chưa? chạy: kubectl apply -f GoMirai/k8s/namespace.yaml"
}
