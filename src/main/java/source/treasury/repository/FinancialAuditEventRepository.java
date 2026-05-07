package source.treasury.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.treasury.entity.FinancialAuditEventEntity;

import java.util.Optional;
@Repository
public interface FinancialAuditEventRepository extends JpaRepository<FinancialAuditEventEntity, Long> {

    Optional<FinancialAuditEventEntity> findTopByOrderBySequenceNumberDesc();
}
