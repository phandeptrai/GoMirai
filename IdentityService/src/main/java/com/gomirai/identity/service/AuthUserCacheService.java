package com.gomirai.identity.service;

import com.gomirai.identity.model.AuthUser;
import com.gomirai.identity.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthUserCacheService {

    private final AuthUserRepository authUserRepository;

    @Cacheable(cacheNames = "auth_user_by_id", key = "#userId.toString()", unless = "#result == null")
    public AuthUser findByIdCached(UUID userId) {
        return authUserRepository.findById(userId).orElse(null);
    }

    @Cacheable(cacheNames = "auth_user_by_phone", key = "#phoneNumber", unless = "#result == null")
    public AuthUser findByPhoneCached(String phoneNumber) {
        return authUserRepository.findByPhoneNumber(phoneNumber).orElse(null);
    }

    /**
     * Targeted eviction: only clear the cache entries for the specific user.
     * Prevents "Cache Evict Storm" where every registration clears the entire cache.
     */
    @Caching(evict = {
        @CacheEvict(cacheNames = "auth_user_by_id", key = "#user.userId.toString()"),
        @CacheEvict(cacheNames = "auth_user_by_phone", key = "#user.phoneNumber", condition = "#user.phoneNumber != null")
    })
    public AuthUser saveAndEvict(AuthUser user) {
        return authUserRepository.save(user);
    }
}

