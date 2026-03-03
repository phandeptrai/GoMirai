package com.gomirai.driver.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.gomirai.common.enums.DriverAccountStatus;
import com.gomirai.common.enums.DriverAvailabilityStatus;
import com.gomirai.common.enums.VehicleType;
import com.gomirai.common.exception.BusinessException;
import com.gomirai.common.exception.NotFoundException;
import com.gomirai.common.security.SecurityUtils;
import com.gomirai.driver.controller.DriverProfileController;
import com.gomirai.driver.dto.request.DriverApplicationRequest;
import com.gomirai.driver.dto.request.DriverVehicleRequest;
import com.gomirai.driver.dto.response.DriverProfileResponse;
import com.gomirai.driver.dto.response.DriverStatusResponse;
import com.gomirai.driver.messaging.DriverApprovalEventsProducer;
import com.gomirai.driver.messaging.DriverAvailabilityEventsProducer;
import com.gomirai.driver.model.DriverProfile;
import com.gomirai.driver.repository.DriverProfileRepository;

@ExtendWith(MockitoExtension.class)
class DriverProfileServiceTest {

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private DriverAvailabilityEventsProducer availabilityEventsProducer;

    @Mock
    private DriverApprovalEventsProducer approvalEventsProducer;

    @Mock
    private com.gomirai.driver.client.UserServiceClient userServiceClient;

    @Mock
    private com.gomirai.driver.client.ReviewServiceClient reviewServiceClient;

    @Mock
    private DriverBookingService driverBookingService;

    private DriverProfileService driverProfileService;

    private DriverProfileController driverProfileController;

    private UUID userId;
    private UUID driverId;
    private DriverProfile mockProfile;

    @BeforeEach
    void setUp() {
        // Khởi tạo Service
        driverProfileService = new DriverProfileService(
                driverProfileRepository,
                securityUtils,
                availabilityEventsProducer,
                approvalEventsProducer,
                userServiceClient,
                reviewServiceClient);

        // Khởi tạo Controller
        driverProfileController = new DriverProfileController(
                driverProfileService,
                driverBookingService,
                securityUtils);

        userId = UUID.randomUUID();
        driverId = UUID.randomUUID();

        mockProfile = new DriverProfile();
        mockProfile.setDriverId(driverId);
        mockProfile.setUserId(userId);
        mockProfile.setAccountStatus(DriverAccountStatus.PENDING_VERIFICATION);
        mockProfile.setAvailabilityStatus(DriverAvailabilityStatus.OFFLINE);
    }

    // ==========================================
    // SERVICE LAYER TESTS
    // ==========================================

    @Test
    @DisplayName("Service: 1.1 Apply to be Driver SUCCESS")
    void apply_Success() {
        // Arrange
        DriverApplicationRequest request = new DriverApplicationRequest(
                "79C1-123456", "Honda", "City", "51A-99999", "White", VehicleType.CAR_4, LocalDate.parse("2023-01-01"));

        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(driverProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(driverProfileRepository.save(any(DriverProfile.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        DriverProfileResponse response = driverProfileService.apply(request);

        // Assert
        assertNotNull(response);
        assertEquals(DriverAccountStatus.PENDING_VERIFICATION, response.accountStatus());
        assertEquals("79C1-123456", response.licenseNumber());
        verify(driverProfileRepository).save(any(DriverProfile.class));
    }

    @Test
    @DisplayName("Service: 2.2 Approve Driver SUCCESS")
    void approve_Success() {
        // Arrange
        when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(mockProfile));
        when(driverProfileRepository.save(any(DriverProfile.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        DriverProfileResponse response = driverProfileService.approve(driverId);

        // Assert
        assertNotNull(response);
        assertEquals(DriverAccountStatus.ACTIVE, response.accountStatus());
        verify(approvalEventsProducer).publishDriverApproved(any());
    }

    @Test
    @DisplayName("Service: 3.1 Go Online SUCCESS")
    void setAvailabilityOnline_Success() {
        // Arrange: Tài xế phải ACTIVE mới được Online
        mockProfile.setAccountStatus(DriverAccountStatus.ACTIVE);
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(driverProfileRepository.findByUserId(userId)).thenReturn(Optional.of(mockProfile));

        // Act
        DriverStatusResponse response = driverProfileService.setAvailabilityOnline();

        // Assert
        assertNotNull(response);
        assertEquals(DriverAvailabilityStatus.ONLINE, response.availabilityStatus());
        verify(availabilityEventsProducer).publishAvailabilityChanged(any());
    }

    @Test
    @DisplayName("Service: 3.1 Go Online FAIL (Not Active)")
    void setAvailabilityOnline_Fail_NotActive() {
        // Arrange: Hồ sơ vẫn đang PENDING
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(driverProfileRepository.findByUserId(userId)).thenReturn(Optional.of(mockProfile));

        // Act & Assert
        assertThrows(BusinessException.class, () -> driverProfileService.setAvailabilityOnline());
    }

    @Test
    @DisplayName("Service: 3.2 Update Vehicle Info SUCCESS")
    void updateCurrentVehicle_Success() {
        // Arrange
        DriverVehicleRequest request = new DriverVehicleRequest(
                "VinFast", "VF8", "51H-88888", "Blue", VehicleType.CAR_4, LocalDate.parse("2024-01-01"));
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(driverProfileRepository.findByUserId(userId)).thenReturn(Optional.of(mockProfile));

        // Act
        var response = driverProfileService.updateCurrentVehicle(request);

        // Assert
        assertNotNull(response);
        assertEquals("VinFast", response.brand());
        assertEquals("51H-88888", response.plateNumber());
    }

    // ==========================================
    // CONTROLLER LAYER TESTS (UNIT)
    // ==========================================

    @Test
    @DisplayName("Controller: 1.1 Apply SUCCESS")
    void controller_apply_Success() {
        // Arrange
        DriverApplicationRequest request = new DriverApplicationRequest(
                "79C1-123456", "Honda", "City", "51A-99999", "White", VehicleType.CAR_4, LocalDate.parse("2023-01-01"));

        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(driverProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(driverProfileRepository.save(any(DriverProfile.class))).thenReturn(mockProfile);

        // Act
        ResponseEntity<DriverProfileResponse> response = driverProfileController.apply(request);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(driverId, response.getBody().driverId());
        verify(driverProfileRepository).save(any(DriverProfile.class));
    }

    @Test
    @DisplayName("Controller: 5.1 Get Rating FAIL (Not Found)")
    void controller_getRating_FakeId_ThrowsNotFoundException() {
        // Arrange
        UUID fakeId = UUID.randomUUID();
        when(driverProfileRepository.findById(fakeId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NotFoundException.class, () -> driverProfileController.getRating(fakeId));
    }

    @Test
    @DisplayName("Controller: Admin Actions - Approve SUCCESS")
    void controller_approve_Success() {
        // Arrange
        when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(mockProfile));
        when(driverProfileRepository.save(any(DriverProfile.class))).thenReturn(mockProfile);

        // Act
        ResponseEntity<DriverProfileResponse> response = driverProfileController.approve(driverId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(driverProfileRepository).save(any(DriverProfile.class));
    }

}
