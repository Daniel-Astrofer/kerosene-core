package source.kfe.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import source.kfe.model.KfeAuditLogEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KfeAuditLogRepository extends JpaRepository<KfeAuditLogEntity, Long> {

    Optional<KfeAuditLogEntity> findTopByOrderBySequenceNumberDesc();

    List<KfeAuditLogEntity> findAllByOrderBySequenceNumberAsc();

    List<KfeAuditLogEntity> findAllByOrderBySequenceNumberDesc(Pageable pageable);

    List<KfeAuditLogEntity> findByTransactionIdOrderBySequenceNumberAsc(UUID transactionId);
}
