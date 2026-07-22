package source.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.notification.model.entity.NotificationDeviceTokenEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationDeviceTokenRepository extends JpaRepository<NotificationDeviceTokenEntity, Long> {

    Optional<NotificationDeviceTokenEntity> findByTokenHash(String tokenHash);

    Optional<NotificationDeviceTokenEntity> findByIdAndUserId(Long id, Long userId);

    List<NotificationDeviceTokenEntity> findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(Long userId);
}
