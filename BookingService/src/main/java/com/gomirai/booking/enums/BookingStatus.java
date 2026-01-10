package com.gomirai.booking.enums;

public enum BookingStatus {
    PENDING,           // Đã tạo, chờ tài xế nhận
    MATCHED,          // Đã có tài xế nhận
    DRIVER_ARRIVED,   // Tài xế đã đến điểm đón
    IN_PROGRESS,      // Đang di chuyển
    COMPLETED,        // Hoàn thành
    CANCELED,         // Đã hủy
    EXPIRED,          // Hết hạn (không có tài xế nhận)
    NO_DRIVER_FOUND   // Không tìm thấy tài xế
}


