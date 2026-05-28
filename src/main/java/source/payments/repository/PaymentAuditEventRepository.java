package source.payments.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.payments.model.PaymentAuditEventEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentAuditEventRepository extends JpaRepository<PaymentAuditEventEntity, UUID> {

    Optional<PaymentAuditEventEntity> findTopByOrderByCreatedAtDesc();
}
