package com.gomirai.ride.booking.controller;

import com.gomirai.ride.booking.dto.request.CancelBookingRequest;
import com.gomirai.ride.booking.dto.request.CompleteBookingRequest;
import com.gomirai.ride.booking.dto.request.CreateBookingRequest;
import com.gomirai.ride.booking.dto.response.BookingResponse;
import com.gomirai.ride.booking.enums.BookingStatus;
import com.gomirai.ride.booking.service.BookingService;
import com.gomirai.common.dto.response.ApiResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller xử lý các API liên quan đến đặt xe.
 * 
 * === CUSTOMER APIs ===
 * - POST /api/booking: Tạo booking mới
 * - GET /api/booking/me: Lấy danh sách booking của customer
 * - POST /api/booking/{id}/cancel: Hủy booking
 * 
 * === DRIVER APIs ===
 * - GET /api/booking/driver/me: Lấy danh sách booking của driver
 * - GET /api/booking/driver/pending: Lấy danh sách booking đang chờ (gần vị trí
 * driver)
 * - PATCH /api/booking/{id}/accept: Driver nhận cuốc
 * - PATCH /api/booking/{id}/arrived: Driver đã đến điểm đón
 * - PATCH /api/booking/{id}/start: Bắt đầu chuyến đi
 * - POST /api/booking/{id}/complete: Hoàn thành chuyến đi
 * 
 * === INTERNAL APIs (cho các service khác gọi) ===
 * - POST /api/booking/{id}/cancel-no-driver: Hủy do không tìm thấy tài xế
 * - GET /api/booking/{id}/info: Lấy thông tin booking (không cần auth)
 * 
 * === Luồng trạng thái booking ===
 * PENDING → MATCHED → DRIVER_ARRIVED → IN_PROGRESS → COMPLETED
 * ↘ CANCELED / NO_DRIVER_FOUND
 */
@RestController
@RequestMapping("/api/booking")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService bookingService;

    /**
     * Create booking
     * POST /api/booking
     */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Bulkhead(name = "bookingCreate", fallbackMethod = "createBookingBulkheadFallback")
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @Valid @RequestBody CreateBookingRequest request) {
        BookingResponse response = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Booking created successfully", response));
    }

    @SuppressWarnings("unused")
    public ResponseEntity<ApiResponse<BookingResponse>> createBookingBulkheadFallback(
            CreateBookingRequest request, Throwable t) {
        log.warn("SLA_BULKHEAD_REJECT bookingCreate: {}", t.toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("OVERLOADED: Too many concurrent booking requests — fail-fast (no queue)"));
    }

    /**
     * Get booking by ID
     * GET /api/booking/{bookingId}
     */
    @GetMapping("/{bookingId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BookingResponse>> getBooking(
            @PathVariable UUID bookingId) {
        BookingResponse response = bookingService.getBooking(bookingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get customer bookings
     * GET /api/booking/me?status=PENDING&page=0&size=10
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getCustomerBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<BookingResponse> response = bookingService.getCustomerBookings(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get driver bookings
     * GET /api/booking/driver/me?status=MATCHED&page=0&size=10
     */
    @GetMapping("/driver/me")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getDriverBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<BookingResponse> response = bookingService.getDriverBookings(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get pending bookings for driver (nearby bookings waiting for driver)
     * GET /api/booking/driver/pending
     */
    @GetMapping("/driver/pending")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getPendingBookings() {
        List<BookingResponse> response = bookingService.getPendingBookingsForDriver();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Cancel booking
     * POST /api/booking/{bookingId}/cancel
     */
    @PostMapping("/{bookingId}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(
            @PathVariable UUID bookingId,
            @Valid @RequestBody CancelBookingRequest request) {
        BookingResponse response = bookingService.cancelBooking(bookingId, request);
        return ResponseEntity.ok(ApiResponse.success("Booking canceled successfully", response));
    }

    /**
     * Driver accept booking
     * PATCH /api/booking/{bookingId}/accept
     */
    @PatchMapping("/{bookingId}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<BookingResponse>> acceptBooking(
            @PathVariable UUID bookingId) {
        BookingResponse response = bookingService.acceptBooking(bookingId);
        return ResponseEntity.ok(ApiResponse.success("Booking accepted successfully", response));
    }

    /**
     * Driver arrived at pickup point
     * PATCH /api/booking/{bookingId}/arrived
     */
    @PatchMapping("/{bookingId}/arrived")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<BookingResponse>> driverArrived(
            @PathVariable UUID bookingId) {
        BookingResponse response = bookingService.driverArrived(bookingId);
        return ResponseEntity.ok(ApiResponse.success("Driver arrived at pickup point", response));
    }

    /**
     * Driver start trip (after customer gets in)
     * PATCH /api/booking/{bookingId}/start
     */
    @PatchMapping("/{bookingId}/start")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<BookingResponse>> startTrip(
            @PathVariable UUID bookingId) {
        BookingResponse response = bookingService.startTrip(bookingId);
        return ResponseEntity.ok(ApiResponse.success("Trip started successfully", response));
    }

    /**
     * Complete booking
     * POST /api/booking/{bookingId}/complete
     */
    @PostMapping("/{bookingId}/complete")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'DRIVER')")
    public ResponseEntity<ApiResponse<BookingResponse>> completeBooking(
            @PathVariable UUID bookingId,
            @Valid @RequestBody CompleteBookingRequest request) {
        BookingResponse response = bookingService.completeBooking(bookingId, request);
        return ResponseEntity.ok(ApiResponse.success("Booking completed successfully", response));
    }

    /**
     * Cancel booking - no driver found (called by TrackingService)
     * POST /api/bookings/{bookingId}/cancel-no-driver
     * Note: This endpoint is called internally by TrackingService, no
     * authentication required
     */
    @PostMapping("/{bookingId}/cancel-no-driver")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBookingNoDriverFound(
            @PathVariable UUID bookingId,
            @RequestBody(required = false) java.util.Map<String, String> request) {
        String reason = request != null && request.containsKey("reason")
                ? request.get("reason")
                : "Không tìm được tài xế trong khu vực";
        BookingResponse response = bookingService.cancelBookingNoDriverFound(bookingId, reason);
        return ResponseEntity.ok(ApiResponse.success("Booking canceled - no driver found", response));
    }

    /**
     * Get booking info for internal service calls (TrackingService)
     * GET /api/booking/{bookingId}/info
     * Note: This endpoint is called internally by TrackingService, no
     * authentication required
     */
    @GetMapping("/{bookingId}/info")
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingInfo(@PathVariable UUID bookingId) {
        BookingResponse response = bookingService.getBookingInternal(bookingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
