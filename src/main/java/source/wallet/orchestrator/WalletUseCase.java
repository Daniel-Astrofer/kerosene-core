package source.wallet.orchestrator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.auth.application.service.authentication.contracts.SignupVerifier;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.UserDataBase;
import source.ledger.service.LedgerService;
import source.wallet.dto.WalletRequestDTO;
import source.wallet.dto.WalletResponseDTO;
import source.wallet.dto.WalletUpdateDTO;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletService;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class WalletUseCase {

    private final UserServiceContract service;
    private final WalletService walletService;
    private final LedgerService ledger;
    private final SignupVerifier verifier;
    private final source.auth.application.service.validation.totp.contratcs.TOTPKeyGenerate totpGenerator;

    public WalletUseCase(UserServiceContract service,
            WalletService walletService, LedgerService ledger, SignupVerifier verifier,
            source.auth.application.service.validation.totp.contratcs.TOTPKeyGenerate totpGenerator) {
        this.service = service;
        this.walletService = walletService;
        this.ledger = ledger;
        this.verifier = verifier;
        this.totpGenerator = totpGenerator;
    }

    @Transactional
    public WalletResponseDTO createWallet(WalletRequestDTO dto, Long userId) {
        UserDataBase db = service.buscarPorId(userId)
                .orElseThrow(() -> new IllegalArgumentException("invalid user"));

        verifier.checkPassphraseBip39(dto.passphrase().toCharArray());

        String nameUpperCase = dto.name() != null ? dto.name().toUpperCase() : null;
        if (walletService.existsByUserIdAndName(userId, nameUpperCase)) {
            throw new WalletExceptions.WalletNameAlredyExists("you are using this name");
        }

        WalletEntity wallet = new WalletEntity();
        // Pass raw passphrase — WalletService.save() centralises BIP39 validation +
        // Argon2 hashing
        wallet.setPassphraseHash(dto.passphrase());
        wallet.setName(nameUpperCase);
        wallet.setUser(db);

        // Generate a specific TOTP secret for THIS wallet
        String totpSecret = totpGenerator.keyGenerator();
        wallet.setTotpSecret(totpSecret);

        walletService.save(wallet);
        ledger.createLedger(wallet, "Initial ledger for new wallet");

        // Format the TOTP URI exactly like we did in SignupUseCase
        String totpUri = String.format(
                source.auth.AuthConstants.TOTP_URI_FORMAT,
                source.auth.AuthConstants.APP_NAME,
                nameUpperCase + " (Wallet)",
                totpSecret,
                source.auth.AuthConstants.APP_NAME);

        // Return the TOTP URI only on wallet creation (one-time) — never return
        // passphrase
        return new WalletResponseDTO(
                wallet.getId(),
                wallet.getName(),
                null, // passphrase is write-only; never returned after storage
                wallet.getCreatedAt(),
                wallet.getUpdatedAt(),
                wallet.getIsActive(),
                totpUri);
    }

    @Transactional
    public void deleteWallet(WalletRequestDTO dto, Long userId) {
        if (!walletService.deleteWallet(userId, dto)) {
            throw new WalletExceptions.WalletNoExists("wallet no exists");
        }
    }

    public List<WalletResponseDTO> getAllWallets(Long userId) {
        return walletService.findByUserId(userId).stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    public WalletResponseDTO getWalletByName(String name, Long userId) {
        // Scoped lookup — wallet is only found if it belongs to this user
        WalletEntity wallet = walletService.findByNameAndUserId(name, userId);
        if (wallet == null) {
            throw new WalletExceptions.WalletNoExists("wallet not found or does not belong to you");
        }
        return mapToResponseDTO(wallet);
    }

    @Transactional
    public void updateWallet(WalletUpdateDTO dto, Long userId) {
        walletService.updateWallet(userId, dto);
    }

    private WalletResponseDTO mapToResponseDTO(WalletEntity entity) {
        return new WalletResponseDTO(
                entity.getId(),
                entity.getName(),
                null, // passphrase is write-only — never exposed after creation
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getIsActive(),
                null);
    }
}
