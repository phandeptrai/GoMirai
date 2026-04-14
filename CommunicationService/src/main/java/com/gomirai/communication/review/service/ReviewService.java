package com.gomirai.communication.review.service;

import com.gomirai.common.exception.BusinessException;
import com.gomirai.communication.review.dto.request.CreateReviewRequest;
import com.gomirai.communication.review.dto.response.RatingSummaryResponse;
import com.gomirai.communication.review.dto.response.ReviewResponse;
import com.gomirai.communication.review.exception.ReviewErrorCode;
import com.gomirai.communication.review.model.Review;
import com.gomirai.communication.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Bổ sung import cần thiết để bắt lỗi DB
import org.springframework.dao.DuplicateKeyException;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository repository;
    private final com.gomirai.communication.review.messaging.DriverRatingEventsProducer driverRatingEventsProducer;

    public ReviewResponse createReview(CreateReviewRequest request, UUID reviewerId) {

        // 1. Business Rule 3: Không thể tự review chính mình
        if (reviewerId.equals(request.getRevieweeId())) {
            throw new BusinessException(ReviewErrorCode.CANNOT_REVIEW_SELF);
        }

        // 2. Business Rule 2: Kiểm tra trùng lặp (Kiểm tra trước để phản hồi nhanh)
        if (repository.existsByBookingIdAndReviewerId(request.getBookingId(), reviewerId)) {
            throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
        }

        // 3. Mapping Request -> Entity
        Review review = new Review();
        review.setReviewId(UUID.randomUUID());
        review.setBookingId(request.getBookingId());
        review.setReviewerId(reviewerId);
        review.setRevieweeId(request.getRevieweeId());
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setCreatedAt(LocalDateTime.now());
        review.setDeleted(false);

        // 4. Lưu vào DB và BẮT LỖI TRÙNG LẶP CỨNG TỪ DB (DuplicateKeyException)
        try {
            Review savedReview = repository.save(review);

            // 5. Tính rating trung bình mới và publish event đến DriverService
            publishDriverRatingUpdate(request.getRevieweeId(), request.getBookingId());

            return mapToResponse(savedReview);
        } catch (DuplicateKeyException e) {
            // Trường hợp kiểm tra trước bị bỏ sót (ví dụ: lỗi mạng thoáng qua)
            // hoặc logic kiểm tra index bị lỗi. Bắt lỗi DB và chuyển thành lỗi nghiệp vụ.
            throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_EXISTS, e);
        }
    }

    /**
     * Tính rating trung bình và publish event đến DriverService
     */
    private void publishDriverRatingUpdate(UUID driverUserId, UUID bookingId) {
        try {
            // Lấy rating summary mới
            RatingSummaryResponse summary = getRatingSummary(driverUserId);

            // Publish event
            driverRatingEventsProducer.publishDriverRatingUpdated(
                    driverUserId,
                    summary.getAverageRating(),
                    summary.getTotalReviews(),
                    bookingId);
        } catch (Exception e) {
            // Log error but don't fail the review creation
            // Rating update is eventually consistent
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn("Failed to publish driver rating update: {}", e.getMessage());
        }
    }

    public Page<ReviewResponse> getReviewsByRevieweeId(UUID revieweeId, Pageable pageable) {
        return repository.findByRevieweeIdAndDeletedFalse(revieweeId, pageable)
                .map(this::mapToResponse);
    }

    public RatingSummaryResponse getRatingSummary(UUID revieweeId) {
        // [ĐÃ SỬA]: Sử dụng phương thức mới findAllByRevieweeIdAndDeletedFalse
        List<Review> reviews = repository.findAllByRevieweeIdAndDeletedFalse(revieweeId);

        if (reviews.isEmpty()) {
            return new RatingSummaryResponse(revieweeId, 0.0, 0, new HashMap<>());
        }

        double average = reviews.stream()
                .mapToDouble(Review::getRating)
                .average()
                .orElse(0.0);

        double roundedAvg = Math.round(average * 10.0) / 10.0; // Làm tròn 1 chữ số thập phân

        Map<Integer, Integer> distribution = new HashMap<>();
        for (int i = 1; i <= 5; i++)
            distribution.put(i, 0); // Khởi tạo 1-5

        for (Review r : reviews) {
            int star = (int) Math.round(r.getRating());
            if (star >= 1 && star <= 5) {
                distribution.put(star, distribution.get(star) + 1);
            }
        }

        return new RatingSummaryResponse(revieweeId, roundedAvg, reviews.size(), distribution);
    }

    /**
     * Check if a booking has been reviewed (by any user).
     * This is used to prevent duplicate reviews and to show/hide review button in
     * frontend.
     */
    public boolean checkReviewExistsByBookingId(UUID bookingId) {
        return repository.existsByBookingId(bookingId);
    }

    private ReviewResponse mapToResponse(Review review) {
        ReviewResponse response = new ReviewResponse();
        BeanUtils.copyProperties(review, response);
        return response;
    }
}