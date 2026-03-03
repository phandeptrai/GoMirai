package com.gomirai.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.gomirai.auth.dto.AuthResponse;
import com.gomirai.auth.dto.LoginRequest;
import com.gomirai.auth.dto.RegisterRequest;
import com.gomirai.auth.messaging.UserEventsProducer;
import com.gomirai.auth.model.AuthUser;
import com.gomirai.auth.repository.AuthUserRepository;
import com.gomirai.common.enums.AuthProvider;
import com.gomirai.common.enums.Role;
import com.gomirai.common.exception.BusinessException;
import com.gomirai.common.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {

    @Mock
    private AuthUserRepository authUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserEventsProducer eventsProducer;

    @Mock
    private GoogleOAuthService googleOAuthService;

    @InjectMocks
    private AuthApplicationService authApplicationService;

    private AuthUser mockUser;
    private final String PHONE_NUMBER = "0987654321";
    private final String RAW_PASSWORD = "TestPass123";
    private final String ENCODED_PASSWORD = "encodedPassword";
    private final UUID USER_ID = UUID.randomUUID();
    private final String MOCK_TOKEN = "mock.jwt.token";

    @BeforeEach
    void setUp() {
        mockUser = new AuthUser();
        mockUser.setUserId(USER_ID);
        mockUser.setPhoneNumber(PHONE_NUMBER);
        mockUser.setPasswordHash(ENCODED_PASSWORD);
        mockUser.setProvider(AuthProvider.LOCAL);
        mockUser.setRole(Role.CUSTOMER);
    }

    @Test
    @DisplayName("1.1.1 Register User (Success) - Ánh xạ từ Postman Collection")
    void register_Success() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setPhoneNumber(PHONE_NUMBER);
        request.setPassword(RAW_PASSWORD);

        when(authUserRepository.existsByPhoneNumber(PHONE_NUMBER)).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        when(authUserRepository.save(any(AuthUser.class))).thenReturn(mockUser);
        when(jwtService.generateToken(any(UUID.class), anyString())).thenReturn(MOCK_TOKEN);

        // Act
        AuthResponse response = authApplicationService.register(request);

        // Assert
        assertNotNull(response);
        assertEquals(USER_ID, response.getUserId());
        assertEquals(MOCK_TOKEN, response.getAccessToken());

        verify(authUserRepository).save(any(AuthUser.class));
        verify(eventsProducer).sendUserRegistered(any());
    }

    @Test
    @DisplayName("1.1.2 Register - Duplicate User - Ánh xạ từ Postman Collection")
    void register_DuplicatePhoneNumber_ThrowsException() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setPhoneNumber(PHONE_NUMBER);
        request.setPassword(RAW_PASSWORD);

        when(authUserRepository.existsByPhoneNumber(PHONE_NUMBER)).thenReturn(true);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authApplicationService.register(request);
        });

        assertTrue(exception.getMessage().contains("tồn tại"));
        verify(authUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("1.2.1 Login Success - Ánh xạ từ Postman Collection")
    void login_Success() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setPhoneNumber(PHONE_NUMBER);
        request.setPassword(RAW_PASSWORD);

        when(authUserRepository.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(jwtService.generateToken(any(UUID.class), anyString())).thenReturn(MOCK_TOKEN);

        // Act
        AuthResponse response = authApplicationService.login(request);

        // Assert
        assertNotNull(response);
        assertEquals(MOCK_TOKEN, response.getAccessToken());
        assertEquals(USER_ID, response.getUserId());
    }

    @Test
    @DisplayName("1.2.2 Login - Wrong Password - Ánh xạ từ Postman Collection")
    void login_WrongPassword_ThrowsException() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setPhoneNumber(PHONE_NUMBER);
        request.setPassword("WrongPass");

        when(authUserRepository.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("WrongPass", ENCODED_PASSWORD)).thenReturn(false);

        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            authApplicationService.login(request);
        });
    }

    @Test
    @DisplayName("1.2.3 Login - User Not Found - Ánh xạ từ Postman Collection")
    void login_UserNotFound_ThrowsException() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setPhoneNumber("0000000000");
        request.setPassword(RAW_PASSWORD);

        when(authUserRepository.findByPhoneNumber("0000000000")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            authApplicationService.login(request);
        });
    }
}
