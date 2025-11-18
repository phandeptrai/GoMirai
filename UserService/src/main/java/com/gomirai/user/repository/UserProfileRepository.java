package com.gomirai.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.gomirai.user.model.UserProfile;

@Repository
public interface UserProfileRepository extends MongoRepository<UserProfile, UUID> {
    Optional<UserProfile> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
}







