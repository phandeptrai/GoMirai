package com.gomirai.review.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.Field; // Dùng Field thay vì MongoId/MongoType

@Data
@Document(collection = "reviews")
@CompoundIndexes({
    @CompoundIndex(name = "unique_booking_review", def = "{'bookingId': 1, 'reviewerId': 1}", unique = true),
    @CompoundIndex(name = "reviewee_time_idx", def = "{'revieweeId': 1, 'createdAt': -1}")
})
public class Review {
    
    // SỬA ĐỔI: Dùng @Field và @Id kết hợp với FieldType.STRING
    @Id 
    @Field(targetType = FieldType.STRING) 
    private UUID reviewId;

    @Indexed
    @Field(targetType = FieldType.STRING) // BẮT BUỘC: Ép kiểu String cho Index 1
    private UUID bookingId;

    @Indexed
    @Field(targetType = FieldType.STRING) // BẮT BUỘC: Ép kiểu String cho Index 2
    private UUID reviewerId; // Người viết review

    @Indexed
    @Field(targetType = FieldType.STRING) // BẮT BUỘC: Ép kiểu String cho Index 3
    private UUID revieweeId; // Người được review

    private double rating;
    private String comment;

    private LocalDateTime createdAt;
    private boolean deleted = false;
}