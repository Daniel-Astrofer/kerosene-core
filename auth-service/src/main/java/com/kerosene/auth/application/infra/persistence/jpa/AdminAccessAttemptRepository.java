package com.kerosene.auth.application.infra.persistence.jpa;

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
import com.kerosene.auth.model.entity.AdminAccessAttemptEntity;
import com.kerosene.auth.model.enums.AdminAccessAttemptStatus;

/** Persistence queries for admin access attempts, including locking of polling transitions. */
@Repository
public interface AdminAccessAttemptRepository extends JpaRepository<AdminAccessAttemptEntity, UUID> {
    /**
     * Loads an access attempt under a pessimistic write lock before polling can transition it.
     *
     * @param id access-attempt identifier
     * @return locked attempt when present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from AdminAccessAttemptEntity attempt where attempt.id = :id")
    Optional<AdminAccessAttemptEntity> findForPollingById(@Param("id") UUID id);

    /** Finds an attempt only when it belongs to the supplied user. */
    Optional<AdminAccessAttemptEntity> findByIdAndUserId(UUID id, Long userId);

    /** Lists unexpired attempts for one user and state, newest request first. */
    List<AdminAccessAttemptEntity> findByUserIdAndStatusAndExpiresAtAfterOrderByRequestedAtDesc(
            Long userId,
            AdminAccessAttemptStatus status,
            LocalDateTime now);
}
