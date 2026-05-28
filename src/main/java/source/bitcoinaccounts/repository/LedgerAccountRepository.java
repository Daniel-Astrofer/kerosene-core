package source.bitcoinaccounts.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import source.bitcoinaccounts.model.LedgerAccountEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerAccountRepository extends JpaRepository<LedgerAccountEntity, UUID> {

    Optional<LedgerAccountEntity> findByBitcoinAccountId(UUID bitcoinAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LedgerAccountEntity l where l.id = :id")
    Optional<LedgerAccountEntity> findByIdForUpdate(@Param("id") UUID id);
}
