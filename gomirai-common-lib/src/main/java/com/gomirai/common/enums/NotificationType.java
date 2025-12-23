package com.gomirai.common.enums;

public enum NotificationType {
    DRIVER_OFFER,     // Driver receives a booking offer (Realtime only)
    BOOKING_UPDATE,   // Booking status changed (Persist + Realtime)
    PAYMENT_SUCCESS,  // Payment completed
    SYSTEM_ALERT      // System maintenance, etc.
}
