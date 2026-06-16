package source.bitcoinaccounts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.ReceivingRequestEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceivingRequestRepository extends JpaRepository<ReceivingRequestEntity, UUID> {

    Optional<ReceivingRequestEntity> findByPublicCode(String publicCode);

    Optional<ReceivingRequestEntity> findTopByAddressIdOrderByCreatedAtDesc(UUID addressId);

    List<ReceivingRequestEntity> findTop50ByCardIdAndStatusNotOrderByCreatedAtDesc(
            UUID cardId,
            BitcoinAccountEnums.ReceivingRequestStatus status);

    List<ReceivingRequestEntity> findTop200ByStatusInAndExpiresAtBeforeOrderByExpiresAtAsc(
            List<BitcoinAccountEnums.ReceivingRequestStatus> statuses,
            LocalDateTime cutoff);

    List<ReceivingRequestEntity> findTop200ByPurgeAfterBefore(LocalDateTime cutoff);
}
