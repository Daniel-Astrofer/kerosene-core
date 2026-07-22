package source.auth.application.infra.persistence.jpa;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import source.auth.model.entity.AdminAccessAttemptEntity;
import source.auth.model.enums.AdminAccessAttemptStatus;

@Repository
public interface AdminAccessAttemptRepository extends JpaRepository<AdminAccessAttemptEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from AdminAccessAttemptEntity attempt where attempt.id = :id")
    Optional<AdminAccessAttemptEntity> findForPollingById(@Param("id") UUID id);

    Optional<AdminAccessAttemptEntity> findByIdAndUserId(UUID id, Long userId);

    List<AdminAccessAttemptEntity> findByUserIdAndStatusAndExpiresAtAfterOrderByRequestedAtDesc(
            Long userId,
            AdminAccessAttemptStatus status,
            LocalDateTime now);
}
