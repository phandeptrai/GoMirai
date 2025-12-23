package com.gomirai.review.controller;

import com.gomirai.review.dto.request.CreateReviewRequest;
import com.gomirai.review.dto.response.RatingSummaryResponse;
import com.gomirai.review.dto.response.ReviewResponse;
import com.gomirai.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
// Các imports cần thiết
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService service;

    /**
     * Lấy UUID của người dùng hiện tại từ Security Context.
     * Phương thức này thay thế cho SecurityUtils.getCurrentUserId()
     * và giả định JwtFilter lưu UUID dưới dạng String trong
     * authentication.getName().
     */
    private UUID getReviewerIdFromContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Vì @PreAuthorize đã chạy, ta chỉ cần kiểm tra xem thông tin có hợp lệ để
        // trích xuất không.
        if (authentication == null || !authentication.isAuthenticated()) {
            // Trường hợp này không nên xảy ra do @PreAuthorize đã chặn.
            // Nếu xảy ra, đây là lỗi logic bảo mật.
            throw new SecurityException("Authentication context missing for authenticated request.");
        }

        // Sử dụng authentication.getName() (thường chứa Subject ID/UUID)
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            // Lỗi xảy ra nếu token có format không phải là UUID (ví dụ: "anonymousUser")
            throw new SecurityException("Invalid Reviewer ID format in JWT Token subject: " + authentication.getName());
        }
    }

    // 1. Tạo Review (MVP Required)
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_DRIVER')")
    public ResponseEntity<ReviewResponse> createReview(@Valid @RequestBody CreateReviewRequest request) {

        // Lấy ID người đang đăng nhập trực tiếp từ Spring Security Context
        UUID reviewerId = getReviewerIdFromContext();

        ReviewResponse response = service.createReview(request, reviewerId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. Lấy danh sách Reviews theo Reviewee ID (MVP Required)
    @GetMapping("/reviewee/{revieweeId}")
    public ResponseEntity<Page<ReviewResponse>> getReviews(
            @PathVariable UUID revieweeId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        // Endpoint này là public (permitAll) nên không cần getCurrentUserId()
        return ResponseEntity.ok(service.getReviewsByRevieweeId(revieweeId, pageable));
    }

    // 3. Lấy Rating Summary (MVP Required)
    @GetMapping("/reviewee/{revieweeId}/rating")
    public ResponseEntity<RatingSummaryResponse> getRatingSummary(@PathVariable UUID revieweeId) {

        // Endpoint này là public (permitAll) nên không cần getCurrentUserId()
        return ResponseEntity.ok(service.getRatingSummary(revieweeId));
    }

    // 4. Check if booking already has a review (for preventing duplicate reviews)
    @GetMapping("/booking/{bookingId}/exists")
    public ResponseEntity<Map<String, Boolean>> checkReviewExists(@PathVariable UUID bookingId) {
        boolean exists = service.checkReviewExistsByBookingId(bookingId);
        return ResponseEntity.ok(Map.of("exists", exists));
    }
}