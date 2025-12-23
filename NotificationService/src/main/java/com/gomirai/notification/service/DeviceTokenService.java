package com.gomirai.notification.service;

import com.gomirai.notification.dto.request.RegisterDeviceRequest;
import com.gomirai.notification.model.UserDeviceToken;
import com.gomirai.notification.repository.UserDeviceTokenRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DeviceTokenService {

    private final UserDeviceTokenRepository repo;

    public DeviceTokenService(UserDeviceTokenRepository repo) {
        this.repo = repo;
    }

    public void register(RegisterDeviceRequest req) {
        UserDeviceToken token = repo
            .findByUserIdAndDeviceType(req.getUserId(), req.getDeviceType())
            .orElse(new UserDeviceToken());

        token.setUserId(req.getUserId());
        token.setDeviceType(req.getDeviceType());
        token.setFcmToken(req.getFcmToken());
        token.setLastUpdated(LocalDateTime.now());

        repo.save(token);
    }
}
