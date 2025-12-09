package com.gomirai.pricing.config;

import org.bson.types.ObjectId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import java.util.UUID;

@ReadingConverter
public class ObjectIdToUuidConverter implements Converter<ObjectId, UUID> {
    @Override
    public UUID convert(ObjectId source) {
        // Tạo UUID từ chuỗi Hex của ObjectId.
        // Đây là cách phổ biến để ánh xạ UUID khi đọc dữ liệu có _id là ObjectId.
        return UUID.nameUUIDFromBytes(source.toHexString().getBytes());
    }
}