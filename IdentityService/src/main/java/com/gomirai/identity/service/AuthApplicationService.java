package com.gomirai.identity.service;

/**
 * CI/CD Trigger Comment: AuthService is ac
 */

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gomirai.identity.dto.AuthResponse;
import com.gomirai.identity.dto.GoogleUserInfo;
import com.gomirai.identity.dto.LoginRequest;
import com.gomirai.identity.dto.RegisterRequest;
import com.gomirai.identity.dto.TokenValidationResponse;
import com.gomirai.common.dto.event.UserRegisteredEvent;
import com.gomirai.common.exception.BusinessException;
import com.gomirai.identity.messaging.UserEventsProducer;
import com.gomirai.common.enums.AuthProvider;
import com.gomirai.identity.model.AuthUser;
import com.gomirai.common.enums.Role;
import com.gomirai.identity.repository.AuthUserRepository;
import com.gomirai.common.security.JwtService;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;

/**
 * Service chính xử lý logic xác thực.
 * 
 * Hỗ trợ 2 phương thức:
 * 1. LOCAL: Đăng ký/đăng nhập bằng số điện thoại + mật khẩu
 * 2. Google OAuth: Xác thực bằng Google ID Token
 */
@Service
@Slf4j
public class AuthApplicationService {

    private final AuthUserRepository authUserRepository;
    private final AuthUserCacheService authUserCacheService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserEventsProducer eventsProducer;
    private final GoogleOAuthService googleOAuthService;
    /**
     * Pool chuyên dụng cho BCrypt (CPU-bound).
     * Tách biệt khỏi Tomcat thread pool để tránh carrier thread pinning.
     * Xem: AsyncBcryptConfig.java để hiểu sizing rationale.
     */
    private final Executor bcryptExecutor;

    /**
     * Wall-clock cap for bcrypt on {@code bcryptExecutor} (internal service SLA class; CPU-bound).
     */
    @Value("${identity.auth.bcrypt-timeout-seconds:5}")
    private int bcryptTimeoutSeconds;

    public AuthApplicationService(AuthUserRepository authUserRepository,
            AuthUserCacheService authUserCacheService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserEventsProducer eventsProducer,
            GoogleOAuthService googleOAuthService,
            @Qualifier("bcryptExecutor") Executor bcryptExecutor) {
        this.authUserRepository = authUserRepository;
        this.authUserCacheService = authUserCacheService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.eventsProducer = eventsProducer;
        this.googleOAuthService = googleOAuthService;
        this.bcryptExecutor = bcryptExecutor;
    }

    /**
     * Đăng ký tài khoản mới với số điện thoại và mật khẩu.
     * 
     * Luồng xử lý:
     * 1. Kiểm tra số điện thoại đã tồn tại chưa (TRƯỚC transaction)
     * 2. Tạo AuthUser mới với role CUSTOMER
     * 3. Gửi event UserRegistered qua Kafka để các service khác xử lý
     * 4. Tạo JWT token và trả về
     */
    public AuthResponse register(RegisterRequest request) {
        // Kiểm tra trùng số điện thoại TRƯỚC khi bắt đầu transaction
        // Điều này giúp tránh lock database không cần thiết
        if (authUserRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new BusinessException("Số điện thoại đã tồn tại");
        }

        // PERF: BCrypt hash should run outside the DB transaction.
        // Bounded wall-clock: bcrypt cannot be capped by HTTP I/O timeouts alone.
        String passwordHash;
        try {
            passwordHash = CompletableFuture
                    .supplyAsync(() -> passwordEncoder.encode(request.getPassword()), bcryptExecutor)
                    .orTimeout(bcryptTimeoutSeconds, TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                log.warn("BCrypt encode exceeded {}s — rejecting register", bcryptTimeoutSeconds);
                throw new BusinessException("Hệ thống đang quá tải, vui lòng thử lại sau");
            }
            log.error("BCrypt encode failed", e);
            throw new BusinessException("Đăng ký thất bại");
        }
        return registerInternal(request, passwordHash);
    }

    /**
     * Phần xử lý đăng ký trong transaction.
     * Tách riêng để đảm bảo transaction chỉ bắt đầu khi thực sự cần save.
     */
    @Transactional
    private AuthResponse registerInternal(RegisterRequest request, String passwordHash) {
        // Tạo user mới với thông tin cơ bản
        AuthUser user = new AuthUser();
        user.setUserId(UUID.randomUUID());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPasswordHash(passwordHash);
        user.setProvider(AuthProvider.LOCAL);
        user.setRole(Role.CUSTOMER); // Mặc định là CUSTOMER
        AuthUser saved = authUserCacheService.saveAndEvict(user);

        // Gửi event để UserService tạo profile và PaymentService tạo wallet
        eventsProducer.sendUserRegistered(
                new UserRegisteredEvent(saved.getUserId(), saved.getPhoneNumber(), saved.getRole().name()));

        // Tạo JWT token chứa userId và role
        String token = jwtService.generateToken(saved.getUserId(), saved.getRole().name());
        return new AuthResponse(saved.getUserId(), saved.getRole().name(), token);
    }

    /**
     * Đăng nhập bằng số điện thoại và mật khẩu.
     * 
     * Luồng xử lý:
     * 1. Tìm user theo số điện thoại
     * 2. So sánh mật khẩu với hash đã lưu
     * 3. Tạo JWT token và trả về
     */
    public AuthResponse login(LoginRequest request) {
        // STEP 1 (I/O): DB lookup — chạy trên VT, park khi đợi MongoDB
        AuthUser user = authUserCacheService.findByPhoneCached(request.getPhoneNumber());
        if (user == null) {
            throw new BusinessException("Thông tin đăng nhập không hợp lệ");
        }

        // STEP 2 (CPU): BCrypt verify — offload sang bcryptExecutor (Platform Threads).
        //
        // Tại sao offload:
        //   - BCrypt là CPU-bound: không có điểm I/O để VT scheduler park.
        //   - Nếu chạy trên VT, nó sẽ "pin" carrier thread trong suốt ~50ms.
        //   - Với 200 VUs đồng thời → 200 carrier threads bị pin → JVM bị nghẽn.
        //   - Offload sang dedicated pool: chỉ tối đa maxPoolSize BCrypt ops chạy song song.
        //     Toàn bộ các request còn lại xếp hàng trong queue của bcryptExecutor,
        //     KHÔNG chiếm carrier thread → JVM scheduler tự do phục vụ các I/O requests khác.
        boolean passwordMatches;
        try {
            passwordMatches = CompletableFuture
                    .supplyAsync(
                            () -> passwordEncoder.matches(request.getPassword(), user.getPasswordHash()),
                            bcryptExecutor)
                    .orTimeout(bcryptTimeoutSeconds, TimeUnit.SECONDS)
                    .join(); // VT-safe: parks VT (not carrier), waiting for bcryptExecutor result
        } catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                log.warn("BCrypt verify exceeded {}s", bcryptTimeoutSeconds);
                throw new BusinessException("Thông tin đăng nhập không hợp lệ");
            }
            log.error("BCrypt verification failed unexpectedly", e);
            throw new BusinessException("Thông tin đăng nhập không hợp lệ");
        }

        if (!passwordMatches) {
            // Trả về message chung để tránh lộ thông tin user tồn tại
            throw new BusinessException("Thông tin đăng nhập không hợp lệ");
        }

        // STEP 3 (CPU-light): JWT signing — nhanh (~1ms), chạy trực tiếp trên VT
        String token = jwtService.generateToken(user.getUserId(), user.getRole().name());
        return new AuthResponse(user.getUserId(), user.getRole().name(), token);
    }

    /**
     * Xác thực bằng Google OAuth.
     * 
     * Google OAuth chỉ thực hiện XÁC THỰC (authentication):
     * - Không phân biệt "đăng ký" hay "đăng nhập" từ góc nhìn người dùng
     * - Nếu providerUserId đã tồn tại → đăng nhập
     * - Nếu chưa tồn tại → tự động tạo tài khoản mới
     * 
     * Luồng xử lý:
     * 1. Xác thực Google ID Token với Google API
     * 2. Lấy thông tin user từ token (email, name, picture)
     * 3. Kiểm tra user đã tồn tại chưa (theo providerUserId)
     * 4. Nếu có → đăng nhập, nếu không → đăng ký
     * 5. Trả về JWT token của hệ thống
     */
    public AuthResponse authenticateWithGoogle(String idToken) {
        log.info("Đang xử lý xác thực Google OAuth");

        // Bước 1: Xác thực token và lấy thông tin user từ Google
        GoogleUserInfo googleUserInfo = googleOAuthService.verifyIdToken(idToken);
        String providerUserId = googleUserInfo.getSub(); // Google user ID (sub claim)

        log.info("Token Google đã xác thực cho email: {} (sub: {})",
                googleUserInfo.getEmail(), providerUserId);

        // Bước 2: Kiểm tra user đã tồn tại với tài khoản Google này chưa
        Optional<AuthUser> existingUser = authUserRepository.findByProviderAndProviderUserId(
                AuthProvider.GOOGLE, providerUserId);

        if (existingUser.isPresent()) {
            // User đã tồn tại → xử lý như ĐĂNG NHẬP
            log.info("Tìm thấy user Google, thực hiện đăng nhập cho userId: {}",
                    existingUser.get().getUserId());
            return loginGoogleUser(existingUser.get(), googleUserInfo);
        } else {
            // User chưa tồn tại → TỰ ĐỘNG ĐĂNG KÝ
            log.info("User Google mới, thực hiện tự động đăng ký cho email: {}",
                    googleUserInfo.getEmail());
            return registerGoogleUser(googleUserInfo);
        }
    }

    /**
     * Đăng nhập user Google đã tồn tại.
     * Cập nhật thông tin từ Google nếu có thay đổi (tên, ảnh).
     */
    @Transactional
    private AuthResponse loginGoogleUser(AuthUser user, GoogleUserInfo googleUserInfo) {
        // Cập nhật thông tin user từ Google (có thể đã thay đổi)
        boolean updated = false;

        // Cập nhật tên nếu khác
        if (googleUserInfo.getName() != null && !googleUserInfo.getName().equals(user.getFullName())) {
            user.setFullName(googleUserInfo.getName());
            updated = true;
        }
        // Cập nhật ảnh đại diện nếu khác
        if (googleUserInfo.getPicture() != null && !googleUserInfo.getPicture().equals(user.getProfilePictureUrl())) {
            user.setProfilePictureUrl(googleUserInfo.getPicture());
            updated = true;
        }
        // Cập nhật email nếu khác
        if (googleUserInfo.getEmail() != null && !googleUserInfo.getEmail().equals(user.getEmail())) {
            user.setEmail(googleUserInfo.getEmail());
            updated = true;
        }

        if (updated) {
            authUserCacheService.saveAndEvict(user);
            log.info("Đã cập nhật thông tin user Google cho userId: {}", user.getUserId());
        }

        String token = jwtService.generateToken(user.getUserId(), user.getRole().name());
        log.info("Đăng nhập Google thành công cho userId: {}", user.getUserId());
        return new AuthResponse(user.getUserId(), user.getRole().name(), token);
    }

    /**
     * Tự động đăng ký user Google mới.
     * 
     * Lưu ý: User OAuth không có số điện thoại ban đầu.
     * Họ có thể thêm số điện thoại sau qua cập nhật profile.
     */
    @Transactional
    private AuthResponse registerGoogleUser(GoogleUserInfo googleUserInfo) {
        // Tạo AuthUser mới cho Google OAuth
        AuthUser user = new AuthUser();
        user.setUserId(UUID.randomUUID());
        user.setProvider(AuthProvider.GOOGLE);
        user.setProviderUserId(googleUserInfo.getSub()); // Google user ID
        user.setEmail(googleUserInfo.getEmail());
        user.setFullName(googleUserInfo.getName());
        user.setProfilePictureUrl(googleUserInfo.getPicture());
        user.setRole(Role.CUSTOMER); // Mặc định là CUSTOMER
        // phoneNumber và passwordHash là null cho user OAuth

        AuthUser saved = authUserCacheService.saveAndEvict(user);
        log.info("Đã tạo user Google OAuth mới với userId: {}", saved.getUserId());

        // Gửi event để UserService tạo profile và PaymentService tạo wallet
        // Sử dụng constructor mở rộng với email và fullName cho user OAuth
        UserRegisteredEvent event = new UserRegisteredEvent(
                saved.getUserId(),
                null, // phoneNumber là null cho user OAuth
                saved.getRole().name(),
                saved.getEmail(),
                saved.getFullName(),
                AuthProvider.GOOGLE.name());
        eventsProducer.sendUserRegistered(event);
        log.info("Đã gửi event UserRegistered cho user Google OAuth: {}", saved.getUserId());

        String token = jwtService.generateToken(saved.getUserId(), saved.getRole().name());
        log.info("Tự động đăng ký Google thành công cho userId: {}", saved.getUserId());
        return new AuthResponse(saved.getUserId(), saved.getRole().name(), token);
    }

    /**
     * Xác thực JWT token.
     * Trích xuất thông tin userId và role từ token.
     */
    public TokenValidationResponse validate(String token) {
        // Parse token và lấy claims
        Optional<Claims> claims = jwtService.parseToken(token);
        if (claims.isEmpty()) {
            return new TokenValidationResponse(false, null, null);
        }

        Claims c = claims.get();
        UUID userId = UUID.fromString(c.getSubject()); // Subject chứa userId
        String role = c.get("role", String.class); // Custom claim "role"
        return new TokenValidationResponse(true, userId, role);
    }

    /**
     * Cập nhật role của user thành DRIVER.
     * Được gọi khi đơn đăng ký tài xế được admin duyệt.
     * 
     * Lưu ý: User cần đăng nhập lại hoặc refresh token
     * để lấy JWT mới với role DRIVER.
     */
    @Transactional
    public void updateUserRoleToDriver(UUID userId) {
        AuthUser user = authUserCacheService.findByIdCached(userId);
        if (user == null) {
            throw new BusinessException("Không tìm thấy user: " + userId);
        }

        // Nếu đã là DRIVER thì không cần cập nhật
        if (user.getRole() == Role.DRIVER) {
            return;
        }

        user.setRole(Role.DRIVER);
        authUserCacheService.saveAndEvict(user);
        log.info("Đã cập nhật role thành DRIVER cho userId: {}", userId);
    }

    /**
     * Làm mới token để lấy role hiện tại từ database.
     * Dùng khi role của user đã được cập nhật.
     */
    public AuthResponse refreshToken(String oldToken) {
        // Parse token cũ để lấy userId
        Optional<Claims> claims = jwtService.parseToken(oldToken);
        if (claims.isEmpty()) {
            throw new BusinessException("Token không hợp lệ");
        }

        UUID userId = UUID.fromString(claims.get().getSubject());

        // Lấy role hiện tại từ database (có thể đã được cập nhật)
        AuthUser user = authUserCacheService.findByIdCached(userId);
        if (user == null) {
            throw new BusinessException("Không tìm thấy user");
        }

        // Tạo token mới với role hiện tại
        String newToken = jwtService.generateToken(user.getUserId(), user.getRole().name());
        return new AuthResponse(user.getUserId(), user.getRole().name(), newToken);
    }
}
