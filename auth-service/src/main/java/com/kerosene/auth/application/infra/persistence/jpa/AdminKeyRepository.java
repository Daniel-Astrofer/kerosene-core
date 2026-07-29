package com.kerosene.auth.application.infra.persistence.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.kerosene.auth.model.entity.AdminKeyEntity;
import com.kerosene.auth.model.enums.AdminKeyStatus;

@Repository
public interface AdminKeyRepository extends JpaRepository<AdminKeyEntity, UUID> {
    Optional<AdminKeyEntity> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, AdminKeyStatus status);

    List<AdminKeyEntity> findByUserIdAndStatus(Long userId, AdminKeyStatus status);
}
