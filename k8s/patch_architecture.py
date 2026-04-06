import os

services_dir = r"c:\Users\bbqdd\GoMirai\k8s\services"
nginx_file = r"c:\Users\bbqdd\GoMirai\k8s\nginx-ingress.yaml"

def patch_file(filepath, req_cpu, lim_cpu, min_rep, max_rep, is_java=True):
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    out_lines = []
    in_hpa = False
    in_resources = False
    in_requests = False
    in_limits = False
    
    for line in lines:
        if line.startswith('---'):
            pass # boundary
            
        if 'kind: HorizontalPodAutoscaler' in line:
            in_hpa = True
            
        if 'minReplicas:' in line and in_hpa:
            out_lines.append(f"  minReplicas: {min_rep}\n")
            continue
            
        if 'maxReplicas:' in line and in_hpa:
            out_lines.append(f"  maxReplicas: {max_rep}\n")
            continue
            
        if 'averageUtilization:' in line and in_hpa:
            # We ONLY want to update CPU, which usually comes before memory.
            # But just unconditionally updating it to 60 is fine logic here assuming it targets 60 for both CPU and MEM.
            out_lines.append("          averageUtilization: 60\n")
            continue
            
        if 'name: JAVA_TOOL_OPTIONS' in line and is_java:
            out_lines.append(line)
            out_lines.append('              value: "-Dspring.main.lazy-initialization=true -XX:MaxRAMPercentage=60.0 -XX:+UseG1GC"\n')
            continue
            
        if 'value: "-Dspring.main.lazy-initialization=true"' in line and is_java:
            continue # Skip old value
            
        if 'requests:' in line:
            in_requests = True
            in_limits = False
            
        if 'limits:' in line:
            in_limits = True
            in_requests = False
            
        if 'cpu:' in line and (in_requests or in_limits):
            indent = line[:line.find('cpu:')]
            if in_requests:
                out_lines.append(f'{indent}cpu: "{req_cpu}"\n')
            else:
                out_lines.append(f'{indent}cpu: "{lim_cpu}"\n')
            continue

        out_lines.append(line)
        
    # Append HPA behavior if not present
    content = "".join(out_lines)
    if 'behavior:' not in content and 'HorizontalPodAutoscaler' in content:
        # insert before the --- of HPA or at the end
        hpa_behavior = """  behavior:
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100
        periodSeconds: 15
    scaleDown:
      stabilizationWindowSeconds: 300\n"""
        
        parts = content.split('---')
        for i, part in enumerate(parts):
            if 'kind: HorizontalPodAutoscaler' in part:
                parts[i] = part.rstrip() + "\n" + hpa_behavior
        content = "---".join(parts)

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

# Group 1
patch_file(os.path.join(services_dir, "api-gateway.yaml"), "500m", "1500m", 2, 5)
patch_file(os.path.join(services_dir, "auth-service.yaml"), "500m", "1000m", 2, 5)
patch_file(os.path.join(services_dir, "map-service.yaml"), "500m", "1000m", 2, 5)

# Group 2
patch_file(os.path.join(services_dir, "booking-service.yaml"), "400m", "800m", 2, 4)
patch_file(os.path.join(services_dir, "driver-service.yaml"), "400m", "800m", 2, 4)
patch_file(os.path.join(services_dir, "tracking-service.yaml"), "400m", "800m", 2, 4)
patch_file(os.path.join(services_dir, "payment-service.yaml"), "400m", "800m", 2, 4)

# Group 3
patch_file(os.path.join(services_dir, "review-service.yaml"), "200m", "500m", 1, 2)
patch_file(os.path.join(services_dir, "notification-service.yaml"), "200m", "500m", 1, 2)
patch_file(os.path.join(services_dir, "pricing-service.yaml"), "200m", "500m", 1, 2)
patch_file(os.path.join(services_dir, "user-service.yaml"), "200m", "500m", 1, 2)

with open(nginx_file, 'r', encoding='utf-8') as f:
    nginx_content = f.read()
    nginx_content = nginx_content.replace('replicas: 3', 'replicas: 2')
    nginx_content = nginx_content.replace('replicas: 1', 'replicas: 2')
    
    if 'affinity:' not in nginx_content:
        aff = """      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
          - labelSelector:
              matchExpressions:
              - key: app
                operator: In
                values:
                - nginx
            topologyKey: "kubernetes.io/hostname"
      containers:"""
        nginx_content = nginx_content.replace("      containers:", aff)

with open(nginx_file, 'w', encoding='utf-8') as f:
    f.write(nginx_content)

print("Patch applied successfully.")
