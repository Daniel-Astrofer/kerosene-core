package source.wallet.orchestrator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.UserDataBase;
import source.ledger.service.LedgerService;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.dto.WalletUpdateDTO;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletService;

import java.util.List;

@Service
public class WalletUseCase {

    private final UserServiceContract service;
    private final WalletService walletService;
    private final LedgerService ledger;

    public WalletUseCase(UserServiceContract service,
            WalletService walletService, LedgerService ledger) {
        this.service = service;
        this.walletService = walletService;
        this.ledger = ledger;
    }

    @Transactional
    public void createWallet(WalletRequestDTO dto, Long userId) {
        UserDataBase db = service.buscarPorId(userId)
                .orElseThrow(() -> new IllegalArgumentException("invalid user"));

        String nameUpperCase = dto.name() != null ? dto.name().toUpperCase() : null;
        if (walletService.existsByUserIdAndName(userId, nameUpperCase)) {
            throw new WalletExceptions.WalletNameAlredyExists("you are using this name");
        }

        WalletEntity wallet = new WalletEntity();
        wallet.setPassphraseHash(dto.passphrase());
        wallet.setName(nameUpperCase);
        wallet.setUser(db);
        walletService.save(wallet);
        ledger.createLedger(wallet, "Initial ledger for new wallet");
    }

    @Transactional
    public void deleteWallet(WalletRequestDTO dto, Long userId) {
        if (!walletService.deleteWallet(userId, dto)) {
            throw new WalletExceptions.WalletNoExists("wallet no exists");
        }
    }

    public List<WalletEntity> getAllWallets(Long userId) {
        service.buscarPorId(userId)
                .orElseThrow(() -> new IllegalArgumentException("invalid user"));
        return walletService.findByUserId(userId);
    }

    public WalletEntity getWalletByName(String name, Long userId) {
        service.buscarPorId(userId)
                .orElseThrow(() -> new IllegalArgumentException("invalid user"));

        WalletEntity wallet = walletService.findByName(name);
        if (wallet == null || !wallet.getUser().getId().equals(userId)) {
            throw new WalletExceptions.WalletNoExists("wallet not found or does not belong to you");
        }
        return wallet;
    }

    @Transactional
    public void updateWallet(WalletUpdateDTO dto, Long userId) {
        service.buscarPorId(userId)
                .orElseThrow(() -> new IllegalArgumentException("invalid user"));
        walletService.updateWallet(userId, dto);
    }
}
