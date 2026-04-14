package com.gomirai.communication.review.repository;

import com.gomirai.communication.review.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends MongoRepository<Review, UUID> {

    // 1. Dùng cho việc lấy danh sách Reviews có phân trang (Public Endpoint)
    Page<Review> findByRevieweeIdAndDeletedFalse(UUID revieweeId, Pageable pageable);

    // 2. Dùng cho việc tính Rating Summary (Lấy TOÀN BỘ list, đã sửa để tránh xung đột)
    List<Review> findAllByRevieweeIdAndDeletedFalse(UUID revieweeId); 
    // Phương thức đã được đổi tên từ findByRevieweeIdAndDeletedFalse

    // Business Rule 2: Check trùng lặp (check if specific reviewer already reviewed the booking)
    boolean existsByBookingIdAndReviewerId(UUID bookingId, UUID reviewerId);

    // Check if booking has ANY review (from any user) - used to prevent multiple reviews
    boolean existsByBookingId(UUID bookingId);
}