package com.gomirai.driver.client;

import com.gomirai.driver.client.dto.UserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Feign Client để gọi UserService.
 * Thay thế RestTemplate thủ công trước đây.
 */
@FeignClient(name = "user-service")
public interface UserServiceClient {

    /**
     * Lấy thông tin user profile public bằng userId.
     * Endpoint: GET /api/users/{userId}/public
     */
    @GetMapping("/api/user/{userId}/public")
    UserProfileResponse getUserPublicInfo(@PathVariable("userId") UUID userId);
}
