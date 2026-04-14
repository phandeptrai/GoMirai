package com.gomirai.communication.review.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ReviewResponse {
    private UUID reviewId;
    private UUID bookingId;
    private UUID reviewerId;
    private UUID revieweeId;
    private double rating;
    private String comment;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}