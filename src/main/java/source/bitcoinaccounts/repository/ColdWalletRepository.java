package source.bitcoinaccounts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.bitcoinaccounts.model.ColdWalletEntity;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface ColdWalletRepository extends JpaRepository<ColdWalletEntity, UUID> {

    Optional<ColdWalletEntity> findByAccountId(UUID accountId);

    List<ColdWalletEntity> findTop100ByOrderByUpdatedAtAsc();
}
