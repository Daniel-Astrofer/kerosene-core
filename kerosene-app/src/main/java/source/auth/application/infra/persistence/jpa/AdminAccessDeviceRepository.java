package source.auth.application.infra.persistence.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.auth.model.entity.AdminAccessDeviceEntity;

@Repository
public interface AdminAccessDeviceRepository extends JpaRepository<AdminAccessDeviceEntity, UUID> {
    Optional<AdminAccessDeviceEntity> findByUserIdAndDeviceId(Long userId, String deviceId);

    List<AdminAccessDeviceEntity> findByUserIdOrderByLastAccessAtDesc(Long userId);
}
