package source.bitcoinaccounts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.PsbtWorkflowEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PsbtWorkflowRepository extends JpaRepository<PsbtWorkflowEntity, UUID> {

    List<PsbtWorkflowEntity> findTop100ByColdWalletIdOrderByCreatedAtDesc(UUID coldWalletId);

    List<PsbtWorkflowEntity> findTop200ByStatusInAndExpiresAtBeforeOrderByExpiresAtAsc(
            List<BitcoinAccountEnums.PsbtStatus> statuses,
            LocalDateTime cutoff);
}
