$services = @("api-gateway", "auth-service", "user-service", "tracking-service", "booking-service", "driver-service", "map-service", "notification-service", "payment-service", "pricing-service", "review-service")

foreach ($svc in $services) {
    Write-Host "Patching $svc..."
    $patch = @{
        spec = @{
            template = @{
                spec = @{
                    containers = @(
                        @{
                            name = $svc
                            env = @(
                                @{
                                    name = "SPRING_APPLICATION_NAME"
                                    value = $svc
                                }
                            )
                        }
                    )
                }
            }
        }
    } | ConvertTo-Json -Depth 10 -Compress
    
    kubectl patch deployment $svc -n gomirai -p $patch
}
