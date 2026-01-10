package com.gomirai.driver.dto.response;

import java.util.UUID;

import com.gomirai.common.enums.DriverAvailabilityStatus;

/**
 * DTO chứa thông tin công khai của tài xế để hiển thị cho customer.
 * Tổng hợp từ DriverService (vehicle, rating) và UserService (name, phone).
 */
public record DriverPublicInfoResponse(
        // Driver profile info
        UUID driverId,
        UUID userId,
        Double rating,
        Integer completedTrips,
        DriverAvailabilityStatus availabilityStatus,

        // Vehicle info
        DriverVehicleResponse vehicle,

        // User info (from UserService)
        String fullName,
        String phone) {
}
