package source.ledger.service;

import source.ledger.dto.LedgerDTO;
import source.ledger.entity.LedgerEntity;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerContract {

    LedgerEntity createLedger(WalletEntity wallet, String context);

    LedgerEntity findByWalletId(Long walletId);

    List<LedgerEntity> findByUserId(Long userId);

    LedgerEntity updateBalance(Long walletId, BigDecimal amount, String context);

    BigDecimal getBalance(Long walletId);

    void deleteLedger(Long walletId);

    LedgerDTO toDTO(LedgerEntity ledger);

    List<LedgerDTO> toDTOList(List<LedgerEntity> ledgers);

    void validateWalletOwnership(WalletEntity wallet, Long userId);

}
