#!/bin/bash
# Note: Do not use 'set -e' here because we want to catch errors to trigger rollback manually

NAMESPACE="gomirai"
SERVICES=("api-gateway" "ride-service" "identity-service")

# Function to rollback all services
function trigger_rollback() {
    echo "❌ [ALERT] Verification Failed! Triggering automatic rollback..."
    for service in "${SERVICES[@]}"; do
        echo "Rolling back $service..."
        kubectl rollout undo deployment/$service -n $NAMESPACE
    done
    echo "⚠️ Rollback sequence completed. Investigation required."
    exit 1
}

# 1. SMOKE TEST
echo "🔍 [VERIFY] Running Smoke Tests..."
k6 run -e BASE_URL=$BASE_URL k6/SmokeTest/main.js
if [ $? -ne 0 ]; then
    echo "Failed at Smoke Test stage."
    trigger_rollback
fi

# 2. WARM UP
echo "🔥 [VERIFY] Warming up system (45s)..."
k6 run -e BASE_URL=$BASE_URL --vus 5 --duration 45s k6/common/warmup.js
if [ $? -ne 0 ]; then
    echo "Failed at Warm-up stage."
    trigger_rollback
fi

# 3. PERFORMANCE GATE
echo "📊 [VERIFY] Running Performance Gate (k6 load)..."
k6 run -e BASE_URL=$BASE_URL k6/LoadTest/login_booking.load.js
if [ $? -ne 0 ]; then
    echo "Failed at Performance Gate stage."
    trigger_rollback
fi

echo "🎉 [SUCCESS] All verification tests passed. Promote to stable."
exit 0
