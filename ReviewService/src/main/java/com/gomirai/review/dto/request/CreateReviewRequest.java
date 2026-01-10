package com.gomirai.review.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.UUID;

@Data
public class CreateReviewRequest {
    @NotNull(message = "Booking ID is required")
    private UUID bookingId;

    @NotNull(message = "Reviewee ID is required")
    private UUID revieweeId;

    @NotNull(message = "Rating is required")
    @DecimalMin(value = "1.0", message = "Rating must be at least 1.0")
    @DecimalMax(value = "5.0", message = "Rating must be at most 5.0")
    private Double rating;

    @Size(max = 500, message = "Comment cannot exceed 500 characters")
    private String comment;
}