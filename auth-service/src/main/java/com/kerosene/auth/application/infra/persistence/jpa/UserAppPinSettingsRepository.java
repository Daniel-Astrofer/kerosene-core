package com.kerosene.auth.application.infra.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.kerosene.auth.model.entity.UserAppPinSettings;

import java.util.Optional;

/** Reads and locks per-user/device application PIN settings for safe factor verification/update flows. */
@Repository
public interface UserAppPinSettingsRepository extends JpaRepository<UserAppPinSettings, Long> {

    /**
     * Finds settings for a user/device pair with a pessimistic write lock.
     *
     * @param userId owning user identifier
     * @param deviceHash stable hash of the registered device
     * @return locked settings row when one exists
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<UserAppPinSettings> findByUserIdAndDeviceHash(Long userId, String deviceHash);
}
