package com.gomirai.review.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gomirai.common.exception.BusinessException;
import com.gomirai.review.dto.request.CreateReviewRequest;
import com.gomirai.review.dto.response.RatingSummaryResponse;
import com.gomirai.review.dto.response.ReviewResponse;
import com.gomirai.review.messaging.DriverRatingEventsProducer;
import com.gomirai.review.model.Review;
import com.gomirai.review.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository repository;

    @Mock
    private DriverRatingEventsProducer driverRatingEventsProducer;

    @InjectMocks
    private ReviewService reviewService;

    private UUID reviewerId;
    private UUID revieweeId;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        reviewerId = UUID.randomUUID();
        revieweeId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
    }

    @Test
    @DisplayName("1. Create Review - Success")
    void createReview_Success() {
        // Arrange
        CreateReviewRequest request = new CreateReviewRequest();
        request.setBookingId(bookingId);
        request.setRevieweeId(revieweeId);
        request.setRating(5.0);
        request.setComment("Excellent!");

        when(repository.existsByBookingIdAndReviewerId(bookingId, reviewerId)).thenReturn(false);
        when(repository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        // Mock summary for event publishing
        Review mockR = new Review();
        mockR.setRating(5.0);
        when(repository.findAllByRevieweeIdAndDeletedFalse(revieweeId)).thenReturn(List.of(mockR));

        // Act
        ReviewResponse response = reviewService.createReview(request, reviewerId);

        // Assert
        assertNotNull(response);
        assertEquals(5.0, response.getRating());
        verify(repository).save(any(Review.class));
        verify(driverRatingEventsProducer).publishDriverRatingUpdated(eq(revieweeId), eq(5.0), eq(1), eq(bookingId));
    }

    @Test
    @DisplayName("2. Create Review - Cannot Review Self")
    void createReview_Self_ThrowsException() {
        // Arrange
        CreateReviewRequest request = new CreateReviewRequest();
        request.setRevieweeId(reviewerId); // Self review

        // Act & Assert
        assertThrows(BusinessException.class, () -> reviewService.createReview(request, reviewerId));
    }

    @Test
    @DisplayName("3. Create Review - Review Already Exists")
    void createReview_Duplicate_ThrowsException() {
        // Arrange
        CreateReviewRequest request = new CreateReviewRequest();
        request.setBookingId(bookingId);
        request.setRevieweeId(revieweeId);

        when(repository.existsByBookingIdAndReviewerId(bookingId, reviewerId)).thenReturn(true);

        // Act & Assert
        assertThrows(BusinessException.class, () -> reviewService.createReview(request, reviewerId));
    }

    @Test
    @DisplayName("4. Get Rating Summary - Correct Math")
    void getRatingSummary_CorrectCalculation() {
        // Arrange
        Review r1 = new Review();
        r1.setRating(5.0);
        Review r2 = new Review();
        r2.setRating(4.0);
        Review r3 = new Review();
        r3.setRating(4.0);

        when(repository.findAllByRevieweeIdAndDeletedFalse(revieweeId)).thenReturn(List.of(r1, r2, r3));

        // Act
        RatingSummaryResponse summary = reviewService.getRatingSummary(revieweeId);

        // Assert
        // Avg = (5+4+4)/3 = 13/3 = 4.333 -> 4.3
        assertEquals(4.3, summary.getAverageRating());
        assertEquals(3, summary.getTotalReviews());
        assertEquals(1, summary.getRatingDistribution().get(5));
        assertEquals(2, summary.getRatingDistribution().get(4));
        assertEquals(0, summary.getRatingDistribution().get(1));
    }
}
