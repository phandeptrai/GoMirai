package com.gomirai.user.service;

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

import com.gomirai.user.dto.UpdateUserProfileRequest;
import com.gomirai.user.dto.UserProfileResponse;
import com.gomirai.user.model.Address;
import com.gomirai.user.model.UserProfile;
import com.gomirai.user.repository.UserProfileRepository;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    private UserProfile mockProfile;
    private final UUID USER_ID = UUID.randomUUID();
    private final String PHONE = "0987654321";

    @BeforeEach
    void setUp() {
        mockProfile = new UserProfile();
        mockProfile.setUserId(USER_ID);
        mockProfile.setPhone(PHONE);
        mockProfile.setFullName("Old Name");
        mockProfile.setEmail("old@test.com");
    }

    @Test
    @DisplayName("2.1.1 Get Profile - Ánh xạ từ Postman Collection")
    void getUserProfile_Success() {
        // Arrange
        when(userProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(mockProfile));

        // Act
        UserProfileResponse response = userProfileService.getUserProfile(USER_ID);

        // Assert
        assertNotNull(response);
        assertEquals(USER_ID, response.getUserId());
        assertEquals(PHONE, response.getPhone());
    }

    @Test
    @DisplayName("2.1.2 Update Profile - Ánh xạ từ Postman Collection")
    void updateUserProfile_Success() {
        // Arrange
        UpdateUserProfileRequest updateRequest = new UpdateUserProfileRequest();
        updateRequest.setFullName("User Da Cap Nhat");
        updateRequest.setEmail("update@test.com");

        Address address = new Address();
        address.setStreet("123 Đường Test");
        address.setCity("Hà Nội");
        updateRequest.setAddress(address);

        when(userProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(mockProfile));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserProfileResponse response = userProfileService.updateUserProfile(USER_ID, updateRequest);

        // Assert
        assertNotNull(response);
        assertEquals("User Da Cap Nhat", response.getFullName());
        assertEquals("update@test.com", response.getEmail());
        assertEquals("Hà Nội", response.getAddress().getCity());

        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    @DisplayName("Get Profile (Not Found/Pending) - Ánh xạ từ logic Service")
    void getUserProfile_NotFound_ReturnsPending() {
        // Arrange
        when(userProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        // Act
        UserProfileResponse response = userProfileService.getUserProfile(USER_ID);

        // Assert
        assertNotNull(response);
        assertEquals(UserProfileResponse.ProfileStatus.PENDING, response.getStatus());
    }
}
