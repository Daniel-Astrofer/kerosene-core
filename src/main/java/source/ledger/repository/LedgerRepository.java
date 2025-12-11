package source.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.ledger.entity.LedgerEntity;

import java.util.List;
import java.util.Optional;


@Repository
public interface LedgerRepository extends JpaRepository<LedgerEntity, Integer> {

    Optional<LedgerEntity> findByWalletId(Long walletId);

    List<LedgerEntity> findByWalletUserId(Long userId);

    boolean existsByWalletId(Long walletId);

    void deleteByWalletId(Long walletId);
}
