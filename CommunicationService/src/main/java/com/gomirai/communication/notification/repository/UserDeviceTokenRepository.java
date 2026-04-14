package com.gomirai.communication.notification.repository;

import com.gomirai.communication.notification.enums.DeviceType;
import com.gomirai.communication.notification.model.UserDeviceToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserDeviceTokenRepository extends MongoRepository<UserDeviceToken, String> {

    Optional<UserDeviceToken> findByUserIdAndDeviceType(UUID userId, DeviceType deviceType);
}
