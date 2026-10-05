package com.kerosene.auth.application.infra.persistence.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.kerosene.auth.model.entity.AdminKeyEntity;
import com.kerosene.auth.model.enums.AdminKeyStatus;

/** Queries administrative signing/approval keys by user and lifecycle state. */
@Repository
public interface AdminKeyRepository extends JpaRepository<AdminKeyEntity, UUID> {
    /** Finds the newest key for a user in the requested state. */
    Optional<AdminKeyEntity> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, AdminKeyStatus status);

    /** Lists all keys for a user in the requested lifecycle state. */
    List<AdminKeyEntity> findByUserIdAndStatus(Long userId, AdminKeyStatus status);
}
