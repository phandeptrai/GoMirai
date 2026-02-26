#!/bin/bash
# =============================================================================
# GoMirai - Tạo K8s Secret ĐỌC THẲNG TỪ .env (BASH VERSION CHO CLOUD SHELL)
# =============================================================================
# Cách dùng:
#   chmod +x k8s/create-secret.sh
#   ./k8s/create-secret.sh
# =============================================================================

NAMESPACE="gomirai"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$DIR/../.env"

if [ ! -f "$ENV_FILE" ]; then
    echo "❌ Không tìm thấy file .env tại: $ENV_FILE"
    echo "💡 Hãy upload file .env của bạn lên Cloud Shell vào thư mục GoMirai/ nhé!"
    exit 1
fi

echo "📄 Đọc .env từ: $ENV_FILE"

# Xóa secret cũ nếu có
kubectl delete secret gomirai-secrets -n $NAMESPACE --ignore-not-found >/dev/null 2>&1

# Khởi tạo lệnh kubectl
CMD="kubectl create secret generic gomirai-secrets --namespace=$NAMESPACE"

# Đọc từng dòng của file .env
while IFS='=' read -r key value; do
    # Bỏ qua comments và dòng trống
    if [[ "$key" =~ ^#.*$ ]] || [ -z "$key" ]; then
        continue
    fi
    
    # Trim khoảng trắng và dấu ngoặc kép
    value=$(echo "$value" | tr -d '"' | tr -d "'" | tr -d '\r' | xargs)
    key=$(echo "$key" | tr -d '\r' | xargs)
    
    export "$key"="$value"
done < "$ENV_FILE"

echo "✅ Đã parse các biến từ .env"

# Map các biến vào lệnh kubectl
CMD="$CMD --from-literal=SECURITY_JWT_SECRET=\"$JWT_SECRET\""

# MongoDB
CMD="$CMD --from-literal=AUTH_MONGODB_URI=\"$AUTH_MONGODB_URI\""
CMD="$CMD --from-literal=USER_MONGODB_URI=\"$USER_MONGODB_URI\""
CMD="$CMD --from-literal=DRIVER_MONGODB_URI=\"$DRIVER_MONGODB_URI\""
CMD="$CMD --from-literal=PRICING_MONGODB_URI=\"$PRICING_MONGODB_URI\""
CMD="$CMD --from-literal=PAYMENT_MONGODB_URI=\"$PAYMENT_MONGODB_URI\""
CMD="$CMD --from-literal=BOOKING_MONGODB_URI=\"$BOOKING_MONGODB_URI\""
CMD="$CMD --from-literal=NOTIFICATION_MONGODB_URI=\"$NOTIFICATION_MONGODB_URI\""
CMD="$CMD --from-literal=REVIEW_MONGODB_URI=\"$REVIEW_MONGODB_URI\""

# Redis
CMD="$CMD --from-literal=SPRING_REDIS_URL=\"$SPRING_REDIS_URL\""
if [[ "$SPRING_REDIS_URL" =~ redis://([^:]*):([^@]*)@([^:]+):([0-9]+) ]]; then
    CMD="$CMD --from-literal=SPRING_REDIS_USERNAME=\"${BASH_REMATCH[1]}\""
    CMD="$CMD --from-literal=SPRING_REDIS_PASSWORD=\"${BASH_REMATCH[2]}\""
    CMD="$CMD --from-literal=SPRING_REDIS_HOST=\"${BASH_REMATCH[3]}\""
    CMD="$CMD --from-literal=SPRING_REDIS_PORT=\"${BASH_REMATCH[4]}\""
    echo "✅ Parse Redis URL thành công: host=${BASH_REMATCH[3]} port=${BASH_REMATCH[4]}"
else
    echo "⚠️  Không parse được Redis URL, set thủ công..."
    CMD="$CMD --from-literal=SPRING_REDIS_HOST=\"\""
    CMD="$CMD --from-literal=SPRING_REDIS_PORT=\"6379\""
    CMD="$CMD --from-literal=SPRING_REDIS_PASSWORD=\"\""
    CMD="$CMD --from-literal=SPRING_REDIS_USERNAME=\"default\""
fi
CMD="$CMD --from-literal=SPRING_REDIS_SSL_ENABLED=\"false\""

# External APIs
# External APIs
CMD="$CMD --from-literal=GOOGLE_CLIENT_ID='${GOOGLE_CLIENT_ID}'"
CMD="$CMD --from-literal=GOOGLE_CLIENT_SECRET='${GOOGLE_CLIENT_SECRET}'"
CMD="$CMD --from-literal=VNPAY_TMN_CODE='${VNPAY_TMN_CODE}'"
CMD="$CMD --from-literal=VNPAY_HASH_SECRET='${VNPAY_HASH_SECRET}'"
CMD="$CMD --from-literal=VNPAY_PAYMENT_URL='${VNPAY_PAYMENT_URL}'"
CMD="$CMD --from-literal=VNPAY_RETURN_URL='${VNPAY_RETURN_URL}'"
CMD="$CMD --from-literal=MAPBOX_ACCESS_TOKEN='${MAPBOX_ACCESS_TOKEN}'"

echo "🔐 Tạo secret 'gomirai-secrets' trong namespace '$NAMESPACE'..."

# Chạy lệnh
eval "$CMD"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Secret tạo thành công!"
    echo "📋 Bạn có thể kiểm tra bằng lệnh:"
    echo "   kubectl get secret gomirai-secrets -n $NAMESPACE"
else
    echo ""
    echo "❌ Tạo secret thất bại!"
fi
