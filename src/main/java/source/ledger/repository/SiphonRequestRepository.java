package source.ledger.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import source.ledger.entity.SiphonRequest;
import source.ledger.entity.SiphonRequestStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiphonRequestRepository extends JpaRepository<SiphonRequest, UUID> {

    Optional<SiphonRequest> findByIdempotencyKey(String idempotencyKey);

    Optional<SiphonRequest> findFirstByStatusInOrderByRequestedAtAsc(Collection<SiphonRequestStatus> statuses);

    List<SiphonRequest> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByRequestedAtAsc(
            Collection<SiphonRequestStatus> statuses,
            LocalDateTime now);

    long countByStatusIn(Collection<SiphonRequestStatus> statuses);

    @Query("SELECT coalesce(max(s.attempts), 0) FROM SiphonRequest s WHERE s.status IN :statuses")
    int maxAttemptsByStatusIn(@Param("statuses") Collection<SiphonRequestStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SiphonRequest s WHERE s.id = :id")
    Optional<SiphonRequest> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SiphonRequest s
               SET s.status = :executingStatus,
                   s.claimedBy = :workerId,
                   s.claimedAt = :now,
                   s.updatedAt = :now
             WHERE s.id = :id
               AND s.nextAttemptAt <= :now
               AND (
                    (s.status IN :dueStatuses AND (s.status <> :failedStatus OR s.retryable = true))
                    OR (s.status = :executingStatus
                        AND s.claimedAt IS NOT NULL
                        AND s.claimedAt <= :staleClaimBefore)
               )
            """)
    int claimDue(
            @Param("id") UUID id,
            @Param("dueStatuses") Collection<SiphonRequestStatus> dueStatuses,
            @Param("executingStatus") SiphonRequestStatus executingStatus,
            @Param("failedStatus") SiphonRequestStatus failedStatus,
            @Param("now") LocalDateTime now,
            @Param("staleClaimBefore") LocalDateTime staleClaimBefore,
            @Param("workerId") String workerId);
}
