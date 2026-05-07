package source.bitcoinaccounts.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.ColdWalletUtxoEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ColdWalletUtxoRepository extends JpaRepository<ColdWalletUtxoEntity, UUID> {

    List<ColdWalletUtxoEntity> findByColdWalletIdAndStatus(UUID coldWalletId, BitcoinAccountEnums.UtxoStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ColdWalletUtxoEntity> findForUpdateByColdWalletIdAndStatus(UUID coldWalletId, BitcoinAccountEnums.UtxoStatus status);

    List<ColdWalletUtxoEntity> findByColdWalletIdAndStatusIn(
            UUID coldWalletId,
            Collection<BitcoinAccountEnums.UtxoStatus> statuses);

    List<ColdWalletUtxoEntity> findByColdWalletId(UUID coldWalletId);

    Optional<ColdWalletUtxoEntity> findByTxidAndVout(String txid, int vout);

    Optional<ColdWalletUtxoEntity> findByColdWalletIdAndTxidAndVout(UUID coldWalletId, String txid, int vout);
}
