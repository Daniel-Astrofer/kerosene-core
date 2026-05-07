package source.transactions.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import source.transactions.model.ExternalProviderOutboxEntity;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExternalProviderOutboxRepository extends JpaRepository<ExternalProviderOutboxEntity, UUID> {

    Optional<ExternalProviderOutboxEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<ExternalProviderOutboxEntity> findTopByTransferIdOrderByCreatedAtDesc(UUID transferId);

    List<ExternalProviderOutboxEntity> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<String> statuses,
            LocalDateTime now);

    long countByStatusIn(Collection<String> statuses);

    Optional<ExternalProviderOutboxEntity> findFirstByStatusInOrderByCreatedAtAsc(Collection<String> statuses);

    @Query("SELECT coalesce(max(o.attempts), 0) FROM ExternalProviderOutboxEntity o WHERE o.status IN :statuses")
    int maxAttemptsByStatusIn(@Param("statuses") Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM ExternalProviderOutboxEntity o WHERE o.id = :id")
    Optional<ExternalProviderOutboxEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ExternalProviderOutboxEntity o
               SET o.status = 'PROCESSING',
                   o.claimedBy = :workerId,
                   o.claimedAt = :now,
                   o.updatedAt = :now
             WHERE o.id = :id
               AND o.nextAttemptAt <= :now
               AND (
                    o.status IN :dueStatuses
                    OR (o.status = 'PROCESSING'
                        AND o.claimedAt IS NOT NULL
                        AND o.claimedAt <= :staleClaimBefore)
               )
            """)
    int claimDue(
            @Param("id") UUID id,
            @Param("dueStatuses") Collection<String> dueStatuses,
            @Param("now") LocalDateTime now,
            @Param("staleClaimBefore") LocalDateTime staleClaimBefore,
            @Param("workerId") String workerId);
}
