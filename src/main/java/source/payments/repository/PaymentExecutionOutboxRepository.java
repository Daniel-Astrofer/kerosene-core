package source.payments.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import source.payments.model.PaymentExecutionOutboxEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentExecutionOutboxRepository extends JpaRepository<PaymentExecutionOutboxEntity, UUID> {

    Optional<PaymentExecutionOutboxEntity> findByPaymentIntentId(UUID paymentIntentId);

    Optional<PaymentExecutionOutboxEntity> findByIdempotencyKey(String idempotencyKey);

    List<PaymentExecutionOutboxEntity> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            List<String> statuses,
            Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PaymentExecutionOutboxEntity o WHERE o.id = :id")
    Optional<PaymentExecutionOutboxEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PaymentExecutionOutboxEntity o
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
            @Param("dueStatuses") List<String> dueStatuses,
            @Param("now") Instant now,
            @Param("staleClaimBefore") Instant staleClaimBefore,
            @Param("workerId") String workerId);
}
