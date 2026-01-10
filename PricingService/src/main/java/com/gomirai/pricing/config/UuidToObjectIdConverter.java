package com.gomirai.pricing.config;

import org.bson.types.ObjectId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import java.util.UUID;

@WritingConverter
public class UuidToObjectIdConverter implements Converter<UUID, ObjectId> {
    @Override
    public ObjectId convert(UUID source) {
        // Khi lưu đối tượng có @Id là UUID, cần chuyển sang ObjectId. 
        // Trả về null sẽ cho phép MongoDB tự tạo ObjectId nếu ruleId là null.
        return null;
    }
}