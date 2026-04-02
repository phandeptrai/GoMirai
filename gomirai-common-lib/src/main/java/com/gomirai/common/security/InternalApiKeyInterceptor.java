package com.gomirai.common.security;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;

import java.io.IOException;

/**
 * REST Interceptor to automatically add X-Internal-Api-Key header to outgoing requests.
 * Used for service-to-service authentication.
 */
public class InternalApiKeyInterceptor implements ClientHttpRequestInterceptor {
    
    private final String internalApiKey;

    public InternalApiKeyInterceptor(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    @Override
    @NonNull
    public ClientHttpResponse intercept(@NonNull HttpRequest request, @NonNull byte[] body, 
                                       @NonNull ClientHttpRequestExecution execution) throws IOException {
        
        if (internalApiKey != null && !internalApiKey.isEmpty()) {
            // Only add if not already present
            if (!request.getHeaders().containsKey(InternalApiKeyFilter.INTERNAL_API_KEY_HEADER)) {
                request.getHeaders().set(InternalApiKeyFilter.INTERNAL_API_KEY_HEADER, internalApiKey);
            }
        }
        
        return execution.execute(request, body);
    }
}
