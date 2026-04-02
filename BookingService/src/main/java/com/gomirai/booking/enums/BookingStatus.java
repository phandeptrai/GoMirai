package com.gomirai.booking.enums;

public enum BookingStatus {
    CREATED, // Mới tạo, chờ làm giàu dữ liệu hoặc thanh toán
    PENDING_PAYMENT, // Chờ trừ tiền (WALLET)
    CONFIRMED, // Thanh toán thành công (hoặc CASH)
    PENDING, // Đã sẵn sàng, chờ tài xế nhận
    MATCHED, // Đã có tài xế nhận
    DRIVER_ARRIVED, // Tài xế đã đến điểm đón
    IN_PROGRESS, // Đang di chuyển
    COMPLETED, // Hoàn thành
    CANCELED, // Đã hủy
    EXPIRED, // Hết hạn (không có tài xế nhận)
    NO_DRIVER_FOUND, // Không tìm thấy tài xế
    FAILED // Lỗi hệ thống hoặc thanh toán thất bại
}
