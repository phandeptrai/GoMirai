package com.gomirai.driver.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.gomirai.common.enums.DriverAccountStatus;
import com.gomirai.driver.model.DriverProfile;

public interface DriverProfileRepository extends MongoRepository<DriverProfile, UUID> {

	Optional<DriverProfile> findByUserId(UUID userId);

	List<DriverProfile> findByAccountStatus(DriverAccountStatus status);

	boolean existsByUserId(UUID userId);
}

