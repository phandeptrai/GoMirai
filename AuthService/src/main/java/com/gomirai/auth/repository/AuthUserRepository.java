package com.gomirai.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.gomirai.auth.model.AuthUser;

public interface AuthUserRepository extends MongoRepository<AuthUser, UUID> {
    Optional<AuthUser> findByPhoneNumber(String phoneNumber);
    boolean existsByPhoneNumber(String phoneNumber);
}



