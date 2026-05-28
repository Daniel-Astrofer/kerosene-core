package source.bitcoinaccounts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.bitcoinaccounts.model.BitcoinAccountEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BitcoinAccountRepository extends JpaRepository<BitcoinAccountEntity, UUID> {

    List<BitcoinAccountEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<BitcoinAccountEntity> findByIdAndUserId(UUID id, Long userId);
}
