package com.gomirai.driver.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RatingSummaryResponse {
    private UUID revieweeId;
    private double averageRating;
    private int totalReviews;
    // Không cần map distribution để tiết kiệm
}
