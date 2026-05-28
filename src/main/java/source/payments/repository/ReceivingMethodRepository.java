package source.payments.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.payments.model.PaymentEnums;
import source.payments.model.ReceivingMethodEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceivingMethodRepository extends JpaRepository<ReceivingMethodEntity, UUID> {

    List<ReceivingMethodEntity> findByUserIdAndStatusOrderByPriorityAsc(
            Long userId,
            PaymentEnums.ReceivingMethodStatus status);

    Optional<ReceivingMethodEntity> findFirstByUserIdAndTypeAndStatusOrderByPriorityAsc(
            Long userId,
            PaymentEnums.ReceivingMethodType type,
            PaymentEnums.ReceivingMethodStatus status);
}
