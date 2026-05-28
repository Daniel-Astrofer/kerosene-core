package source.bitcoinaccounts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.bitcoinaccounts.model.InternalBtcCardEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InternalBtcCardRepository extends JpaRepository<InternalBtcCardEntity, UUID> {

    Optional<InternalBtcCardEntity> findByBitcoinAccountId(UUID bitcoinAccountId);
}
