package source.wallet.application.service;

import org.springframework.stereotype.Component;
import source.wallet.dto.WalletResponseDTO;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletCardProfile;
import source.wallet.service.WalletCardSnapshot;

@Component
public class WalletResponseAssembler {

    public WalletResponseDTO toResponse(
            WalletEntity entity,
            WalletCardProfile cardProfile,
            WalletCardSnapshot cardSnapshot) {
        return toResponse(entity, cardProfile, cardSnapshot, null);
    }

    public WalletResponseDTO toResponse(
            WalletEntity entity,
            WalletCardProfile cardProfile,
            WalletCardSnapshot cardSnapshot,
            String totpUri) {
        return new WalletResponseDTO(
                entity.getId(),
                entity.getName(),
                null,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getIsActive(),
                totpUri,
                entity.getDepositAddress(),
                entity.getLightningAddress(),
                entity.getWalletMode().name(),
                entity.getXpub() != null && !entity.getXpub().isBlank(),
                cardProfile.cardType().name(),
                cardSnapshot.holderName(),
                cardSnapshot.maskedNumber(),
                cardSnapshot.suffix(),
                cardSnapshot.sequence(),
                cardSnapshot.rotationStatus(),
                cardSnapshot.issuedAt(),
                cardSnapshot.expiresAt(),
                cardSnapshot.nextRotationAt(),
                cardSnapshot.lastRotatedAt(),
                cardSnapshot.previousSuffix(),
                cardSnapshot.previousExpiresAt(),
                cardProfile.withdrawalFeeRate(),
                cardProfile.depositFeeRate());
    }
}
