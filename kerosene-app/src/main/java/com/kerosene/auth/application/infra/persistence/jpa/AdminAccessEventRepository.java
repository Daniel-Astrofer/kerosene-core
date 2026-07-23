package com.kerosene.auth.application.infra.persistence.jpa;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.kerosene.auth.model.entity.AdminAccessEventEntity;

@Repository
public interface AdminAccessEventRepository extends JpaRepository<AdminAccessEventEntity, UUID> {
}
