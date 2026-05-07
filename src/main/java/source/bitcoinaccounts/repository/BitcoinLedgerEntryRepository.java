package source.bitcoinaccounts.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.bitcoinaccounts.model.LedgerEntryEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BitcoinLedgerEntryRepository extends JpaRepository<LedgerEntryEntity, UUID> {

    Optional<LedgerEntryEntity> findByIdempotencyKey(String idempotencyKey);
}
