package source.wallet.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.wallet.application.port.out.WalletPersistencePort;
import source.wallet.model.WalletEntity;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class WalletCardLifecycleService {

    private final WalletPersistencePort walletPersistencePort;
    private final int cardValidityMonths;
    private final int expiringSoonDays;

    public WalletCardLifecycleService(
            WalletPersistencePort walletPersistencePort,
            @Value("${wallet.card.validity-months:24}") int cardValidityMonths,
            @Value("${wallet.card.expiring-soon-days:14}") int expiringSoonDays) {
        this.walletPersistencePort = walletPersistencePort;
        this.cardValidityMonths = cardValidityMonths;
        this.expiringSoonDays = expiringSoonDays;
    }

    @Transactional
    public WalletCardSnapshot resolve(WalletEntity wallet) {
        WalletEntity effective = initializeIfMissing(wallet);
        effective = rotateIfExpired(effective);

        LocalDateTime now = LocalDateTime.now();
        String status = "ACTIVE";
        if (effective.getCardLastRotatedAt() != null
                && effective.getCardLastRotatedAt().isAfter(now.minusHours(24))) {
            status = "ROTATING";
        } else if (effective.getCardExpiresAt() != null
                && effective.getCardExpiresAt().isBefore(now.plusDays(expiringSoonDays))) {
            status = "EXPIRING";
        }

        return new WalletCardSnapshot(
                effective.getName(),
                buildMaskedNumber(effective),
                safeSuffix(effective),
                effective.getCardSequence() != null ? effective.getCardSequence() : 1,
                status,
                effective.getCardIssuedAt(),
                effective.getCardExpiresAt(),
                effective.getCardExpiresAt(),
                effective.getCardLastRotatedAt(),
                effective.getPreviousCardNumberSuffix(),
                effective.getPreviousCardExpiresAt());
    }

    private WalletEntity initializeIfMissing(WalletEntity wallet) {
        if (wallet.getCardNumberSuffix() != null
                && !wallet.getCardNumberSuffix().isBlank()
                && wallet.getCardIssuedAt() != null
                && wallet.getCardExpiresAt() != null
                && wallet.getCardSequence() != null
                && wallet.getCardSequence() > 0) {
            return wallet;
        }

        LocalDateTime issuedAt = wallet.getCreatedAt() != null ? wallet.getCreatedAt() : LocalDateTime.now();
        wallet.setCardSequence(wallet.getCardSequence() != null && wallet.getCardSequence() > 0 ? wallet.getCardSequence() : 1);
        wallet.setCardIssuedAt(issuedAt);
        wallet.setCardExpiresAt(issuedAt.plusMonths(cardValidityMonths));
        wallet.setCardNumberSuffix(generateSuffix(wallet, wallet.getCardSequence()));
        return walletPersistencePort.save(wallet);
    }

    private WalletEntity rotateIfExpired(WalletEntity wallet) {
        LocalDateTime expiresAt = wallet.getCardExpiresAt();
        if (expiresAt == null || !LocalDateTime.now().isAfter(expiresAt)) {
            return wallet;
        }

        int nextSequence = (wallet.getCardSequence() != null ? wallet.getCardSequence() : 1) + 1;
        wallet.setPreviousCardNumberSuffix(wallet.getCardNumberSuffix());
        wallet.setPreviousCardExpiresAt(wallet.getCardExpiresAt());
        wallet.setCardSequence(nextSequence);
        wallet.setCardIssuedAt(LocalDateTime.now());
        wallet.setCardExpiresAt(LocalDateTime.now().plusMonths(cardValidityMonths));
        wallet.setCardLastRotatedAt(LocalDateTime.now());
        wallet.setCardNumberSuffix(generateSuffix(wallet, nextSequence));
        return walletPersistencePort.save(wallet);
    }

    private String buildMaskedNumber(WalletEntity wallet) {
        String suffix = safeSuffix(wallet);
        int issuer = 5300 + Math.abs(Objects.hash(wallet.getName(), wallet.getId())) % 500;
        int account = Math.abs(Objects.hash(wallet.getUser().getId(), wallet.getId())) % 10000;
        int sequence = wallet.getCardSequence() != null ? wallet.getCardSequence() % 10000 : 1;
        return String.format("%04d %04d **** %s", issuer, account + sequence, suffix);
    }

    private String safeSuffix(WalletEntity wallet) {
        String suffix = wallet.getCardNumberSuffix();
        if (suffix == null || suffix.isBlank()) {
            return generateSuffix(wallet, wallet.getCardSequence() != null ? wallet.getCardSequence() : 1);
        }
        return suffix;
    }

    private String generateSuffix(WalletEntity wallet, int sequence) {
        int raw = Math.abs(Objects.hash(wallet.getUser().getId(), wallet.getId(), wallet.getName(), sequence)) % 10000;
        return String.format("%04d", raw);
    }
}
