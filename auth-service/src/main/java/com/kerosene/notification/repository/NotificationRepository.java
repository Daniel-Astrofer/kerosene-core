package com.kerosene.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.kerosene.notification.model.entity.NotificationEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    List<NotificationEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<NotificationEntity> findByIdAndUserId(Long id, Long userId);
    int countByUserIdAndReadFalse(Long userId);
}
