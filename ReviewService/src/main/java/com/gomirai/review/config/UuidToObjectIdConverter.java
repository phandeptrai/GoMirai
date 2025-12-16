package com.gomirai.review.config;

import org.bson.types.ObjectId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import java.util.UUID;

@WritingConverter
public class UuidToObjectIdConverter implements Converter<UUID, ObjectId> {
    @Override
    public ObjectId convert(UUID source) {
        return null;
    }
}