package com.kerosene.auth.application.infra.persistence.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.kerosene.auth.model.entity.AdminAccessDeviceEntity;

/** Persistence queries for administrator device enrollment and last-access ordering. */
@Repository
public interface AdminAccessDeviceRepository extends JpaRepository<AdminAccessDeviceEntity, UUID> {
    /** Finds a specific enrolled device owned by a user. */
    Optional<AdminAccessDeviceEntity> findByUserIdAndDeviceId(Long userId, String deviceId);

    /** Lists one user's devices from most recently accessed to least recently accessed. */
    List<AdminAccessDeviceEntity> findByUserIdOrderByLastAccessAtDesc(Long userId);
}
