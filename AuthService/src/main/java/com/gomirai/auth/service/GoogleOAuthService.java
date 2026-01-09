package com.gomirai.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.gomirai.auth.dto.GoogleUserInfo;
import com.gomirai.common.exception.BusinessException;

import lombok.extern.slf4j.Slf4j;

/**
 * Service xác thực Google OAuth Token.
 * 
 * Luồng xác thực Google OAuth:
 * 1. Client sử dụng Google Sign-In SDK để lấy ID Token
 * 2. Client gửi ID Token đến AuthService
 * 3. AuthService xác thực token với Google tokeninfo endpoint
 * 4. AuthService trích xuất thông tin user và tạo/cập nhật tài khoản
 * 
 * Lưu ý: Service này chỉ thực hiện XÁC THỰC (authentication).
 * - Không phân biệt "đăng ký" hay "đăng nhập" từ góc nhìn người dùng
 * - Nếu user đã tồn tại (theo providerUserId) → đăng nhập
 * - Nếu user chưa tồn tại → tự động đăng ký
 */
@Service
@Slf4j
public class GoogleOAuthService {

    /**
     * URL endpoint của Google để xác thực ID Token.
     * Trả về thông tin user nếu token hợp lệ.
     */
    private static final String GOOGLE_TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    /**
     * Google Client ID của ứng dụng.
     * Dùng để validate audience claim trong token.
     */
    @Value("${google.oauth.client-id}")
    private String googleClientId;

    private final RestTemplate restTemplate;

    public GoogleOAuthService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Xác thực Google ID Token và trích xuất thông tin user.
     * 
     * Sử dụng Google tokeninfo endpoint để xác thực:
     * - Chữ ký token (signature)
     * - Thời hạn token (expiration)
     * - Audience phải khớp với client ID của app
     * 
     * @param idToken Google ID Token từ client
     * @return GoogleUserInfo chứa thông tin user từ Google
     * @throws BusinessException nếu token không hợp lệ
     */
    public GoogleUserInfo verifyIdToken(String idToken) {
        log.info("Đang xác thực Google ID Token");

        try {
            // Gọi Google API để xác thực token
            String url = GOOGLE_TOKEN_INFO_URL + idToken;
            ResponseEntity<GoogleUserInfo> response = restTemplate.getForEntity(url, GoogleUserInfo.class);

            // Kiểm tra response từ Google
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Xác thực Google token thất bại: {}", response.getStatusCode());
                throw new BusinessException("Google ID Token không hợp lệ");
            }

            GoogleUserInfo userInfo = response.getBody();

            // Validate sub claim (Google user ID) - bắt buộc phải có
            if (userInfo.getSub() == null || userInfo.getSub().isBlank()) {
                log.error("Google token thiếu sub claim");
                throw new BusinessException("Google ID Token không hợp lệ: thiếu user ID");
            }

            // Validate email - bắt buộc phải có cho việc tạo tài khoản
            if (userInfo.getEmail() == null || userInfo.getEmail().isBlank()) {
                log.error("Google token thiếu email");
                throw new BusinessException("Google ID Token không hợp lệ: thiếu email");
            }

            log.info("Xác thực Google token thành công cho email: {}", userInfo.getEmail());
            return userInfo;

        } catch (RestClientException e) {
            // Lỗi khi gọi Google API (network, timeout, etc.)
            log.error("Lỗi khi xác thực Google ID Token: {}", e.getMessage());
            throw new BusinessException("Lỗi khi xác thực Google ID Token: " + e.getMessage());
        }
    }
}
