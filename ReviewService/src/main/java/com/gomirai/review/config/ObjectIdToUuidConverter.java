package com.gomirai.review.config;

import org.bson.types.ObjectId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import java.util.UUID;

@ReadingConverter
public class ObjectIdToUuidConverter implements Converter<ObjectId, UUID> {
    @Override
    public UUID convert(ObjectId source) {
        return UUID.nameUUIDFromBytes(source.toHexString().getBytes());
    }
}