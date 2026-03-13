package source.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.ledger.entity.LedgerEntity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerRepository extends JpaRepository<LedgerEntity, Integer> {

    Optional<LedgerEntity> findByWalletId(Long walletId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LedgerEntity l WHERE l.wallet.id = :walletId")
    Optional<LedgerEntity> findByWalletIdForUpdate(@Param("walletId") Long walletId);

    @Query("SELECT l FROM LedgerEntity l JOIN FETCH l.wallet WHERE l.wallet.user.id = :userId")
    List<LedgerEntity> findByWalletUserId(@Param("userId") Long userId);

    boolean existsByWalletId(Long walletId);

    void deleteByWalletId(Long walletId);
}
