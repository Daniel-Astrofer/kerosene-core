package source.kfe.application.transaction;

import org.springframework.stereotype.Service;
import source.kfe.dto.KfeSubmitTransactionRequest;
import source.kfe.model.KfeDirection;
import source.kfe.model.KfeWalletEntity;
import source.kfe.model.KfeWalletKind;
import source.kfe.model.KfeWalletStatus;
import source.kfe.repository.KfeWalletRepository;

@Service
public class KfeTransactionWalletResolver {

    private final KfeWalletRepository walletRepository;

    public KfeTransactionWalletResolver(KfeWalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    public KfeWalletEntity resolveSourceWallet(Long userId, KfeSubmitTransactionRequest request) {
        if (!requiresSourceReserve(request)) {
            return null;
        }
        if (request.sourceWalletId() == null) {
            throw new IllegalArgumentException("sourceWalletId is required.");
        }
        KfeWalletEntity wallet = walletRepository.findByIdAndUserIdForUpdate(request.sourceWalletId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Source KFE wallet not found."));
        requireSpendable(wallet, "source");
        return wallet;
    }

    public KfeWalletEntity resolveDestinationWallet(Long userId, KfeSubmitTransactionRequest request) {
        if (request.direction() == KfeDirection.OUTBOUND) {
            return null;
        }
        if (request.destinationWalletId() == null) {
            throw new IllegalArgumentException("destinationWalletId is required.");
        }
        KfeWalletEntity wallet = walletRepository.findById(request.destinationWalletId())
                .orElseThrow(() -> new IllegalArgumentException("Destination KFE wallet not found."));
        if (request.direction() == KfeDirection.INBOUND && !wallet.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Inbound destination wallet must belong to the authenticated user.");
        }
        requireSpendable(wallet, "destination");
        return wallet;
    }

    public boolean requiresSourceReserve(KfeSubmitTransactionRequest request) {
        return request.direction() == KfeDirection.OUTBOUND || request.direction() == KfeDirection.INTERNAL;
    }

    private void requireSpendable(KfeWalletEntity wallet, String role) {
        if (wallet.getStatus() != KfeWalletStatus.ACTIVE) {
            throw new IllegalStateException(role + " wallet is not active.");
        }
        if (wallet.getKind() == KfeWalletKind.WATCH_ONLY || !wallet.isSpendable()) {
            throw new IllegalStateException(role + " wallet is watch-only and cannot move funds.");
        }
    }
}
