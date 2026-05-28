package source.auth.application.infra.persistence.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.auth.model.entity.AdminAccessAttemptEntity;
import source.auth.model.enums.AdminAccessAttemptStatus;

@Repository
public interface AdminAccessAttemptRepository extends JpaRepository<AdminAccessAttemptEntity, UUID> {
    Optional<AdminAccessAttemptEntity> findByIdAndUserId(UUID id, Long userId);

    List<AdminAccessAttemptEntity> findByUserIdAndStatusAndExpiresAtAfterOrderByRequestedAtDesc(
            Long userId,
            AdminAccessAttemptStatus status,
            LocalDateTime now);
}
