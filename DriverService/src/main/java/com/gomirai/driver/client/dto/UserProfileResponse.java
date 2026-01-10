package com.gomirai.driver.client.dto;

import java.util.UUID;

/**
 * DTO để nhận response từ UserService.
 * Chỉ chứa các field public cần thiết.
 */
public record UserProfileResponse(
        UUID userId,
        String fullName,
        String phone,
        String email) {
}
