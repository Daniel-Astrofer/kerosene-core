package source.bitcoinaccounts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.bitcoinaccounts.model.TaxEventEntity;
import source.bitcoinaccounts.model.BitcoinAccountEnums;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxEventRepository extends JpaRepository<TaxEventEntity, UUID> {

    List<TaxEventEntity> findTop200ByPurgeAfterBefore(LocalDateTime cutoff);

    List<TaxEventEntity> findTop500ByUserIdAndPurgeAfterAfterOrderByCreatedAtDesc(Long userId, LocalDateTime cutoff);

    Optional<TaxEventEntity> findByIdAndUserId(UUID id, Long userId);

    Optional<TaxEventEntity> findFirstByUserIdAndEventTypeAndSourceTxid(
            Long userId,
            BitcoinAccountEnums.TaxEventType eventType,
            String sourceTxid);
}
