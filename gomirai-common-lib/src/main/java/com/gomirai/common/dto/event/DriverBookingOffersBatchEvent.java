package com.gomirai.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One Kafka record for multiple driver offers (same booking metadata).
 * Producers: TrackingService. Consumers: DriverService (expands to per-driver handling).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DriverBookingOffersBatchEvent extends BaseEvent {

    private UUID bookingId;
    /** Driver IDs selected by GEO search (order preserved if needed for UI). */
    private List<UUID> driverIds = new ArrayList<>();
    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double dropoffLatitude;
    private Double dropoffLongitude;
    private String vehicleType;
    private Double estimatedDistanceKm;
    private Integer estimatedDurationMinutes;
    private Double estimatedFare;
    private String currency;
    private String pickupAddress;
    private String dropoffAddress;
    /** True when offer is from radius expansion vs initial search. */
    private boolean expansion;

    public static DriverBookingOffersBatchEvent fromBookingContext(
            UUID bookingId,
            List<UUID> driverIds,
            Double pickupLatitude,
            Double pickupLongitude,
            Double dropoffLatitude,
            Double dropoffLongitude,
            String vehicleType,
            Double estimatedDistanceKm,
            Integer estimatedDurationMinutes,
            Double estimatedFare,
            String currency,
            String pickupAddress,
            String dropoffAddress,
            boolean expansion) {
        DriverBookingOffersBatchEvent e = new DriverBookingOffersBatchEvent();
        e.init("DriverBookingOffersBatchEvent", "TrackingService");
        e.setBookingId(bookingId);
        e.setDriverIds(driverIds != null ? new ArrayList<>(driverIds) : new ArrayList<>());
        e.setPickupLatitude(pickupLatitude);
        e.setPickupLongitude(pickupLongitude);
        e.setDropoffLatitude(dropoffLatitude);
        e.setDropoffLongitude(dropoffLongitude);
        e.setVehicleType(vehicleType);
        e.setEstimatedDistanceKm(estimatedDistanceKm);
        e.setEstimatedDurationMinutes(estimatedDurationMinutes);
        e.setEstimatedFare(estimatedFare);
        e.setCurrency(currency);
        e.setPickupAddress(pickupAddress);
        e.setDropoffAddress(dropoffAddress);
        e.setExpansion(expansion);
        return e;
    }
}
