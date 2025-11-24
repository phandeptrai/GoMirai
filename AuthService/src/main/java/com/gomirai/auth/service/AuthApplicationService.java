package com.gomirai.auth.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gomirai.auth.dto.AuthResponse;
import com.gomirai.auth.dto.LoginRequest;
import com.gomirai.auth.dto.RegisterRequest;
import com.gomirai.auth.dto.TokenValidationResponse;
import com.gomirai.common.dto.event.UserRegisteredEvent;
import com.gomirai.common.exception.BusinessException;
import com.gomirai.auth.messaging.UserEventsProducer;
import com.gomirai.common.enums.AuthProvider;
import com.gomirai.auth.model.AuthUser;
import com.gomirai.common.enums.Role;
import com.gomirai.auth.repository.AuthUserRepository;
import com.gomirai.common.security.JwtService;

import io.jsonwebtoken.Claims;

@Service
public class AuthApplicationService {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserEventsProducer eventsProducer;

    public AuthApplicationService(AuthUserRepository authUserRepository,
                                  PasswordEncoder passwordEncoder,
                                  JwtService jwtService,
                                  UserEventsProducer eventsProducer) {
        this.authUserRepository = authUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.eventsProducer = eventsProducer;
    }

    public AuthResponse register(RegisterRequest request) {
        // Kiểm tra trùng số điện thoại TRƯỚC khi bắt đầu transaction
        if (authUserRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new BusinessException("Số điện thoại đã tồn tại");
        }
        
        // Bắt đầu transaction chỉ khi cần save vào DB
        return registerInternal(request);
    }
    
    @Transactional
    private AuthResponse registerInternal(RegisterRequest request) {
        AuthUser user = new AuthUser();
        user.setUserId(UUID.randomUUID());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setProvider(AuthProvider.LOCAL);
        user.setRole(Role.CUSTOMER);
        AuthUser saved = authUserRepository.save(user);

        eventsProducer.sendUserRegistered(new UserRegisteredEvent(saved.getUserId(), saved.getPhoneNumber(), saved.getRole().name()));

        String token = jwtService.generateToken(saved.getUserId(), saved.getRole().name());
        return new AuthResponse(saved.getUserId(), saved.getRole().name(), token);
    }

    public AuthResponse login(LoginRequest request) {
        AuthUser user = authUserRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> new BusinessException("Thông tin đăng nhập không hợp lệ"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Thông tin đăng nhập không hợp lệ");
        }
        String token = jwtService.generateToken(user.getUserId(), user.getRole().name());
        return new AuthResponse(user.getUserId(), user.getRole().name(), token);
    }

    public TokenValidationResponse validate(String token) {
        Optional<Claims> claims = jwtService.parseToken(token);
        if (claims.isEmpty()) {
            return new TokenValidationResponse(false, null, null);
        }
        Claims c = claims.get();
        UUID userId = UUID.fromString(c.getSubject());
        String role = c.get("role", String.class);
        return new TokenValidationResponse(true, userId, role);
    }
}



