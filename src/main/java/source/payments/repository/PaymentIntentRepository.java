package source.payments.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import source.payments.model.PaymentIntentEntity;
import source.payments.model.PaymentEnums;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntentEntity, UUID> {

    Optional<PaymentIntentEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentIntentEntity> findByIdAndSenderUserId(UUID id, Long senderUserId);

    List<PaymentIntentEntity> findTop50ByStatusInOrderByUpdatedAtAsc(
            List<PaymentEnums.PaymentIntentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentIntentEntity p WHERE p.id = :id")
    Optional<PaymentIntentEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentIntentEntity p WHERE p.id = :id AND p.senderUserId = :senderUserId")
    Optional<PaymentIntentEntity> findByIdAndSenderUserIdForUpdate(
            @Param("id") UUID id,
            @Param("senderUserId") Long senderUserId);
}
