package com.gomirai.review.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RatingSummaryResponse {
    private UUID revieweeId;
    private double averageRating;
    private int totalReviews;
    private Map<Integer, Integer> ratingDistribution;
}