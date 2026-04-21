package source.wallet.application.service;

import org.springframework.stereotype.Component;
import source.wallet.dto.WalletResponseDTO;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletCardProfile;

@Component
public class WalletResponseAssembler {

    public WalletResponseDTO toResponse(WalletEntity entity, WalletCardProfile cardProfile) {
        return toResponse(entity, cardProfile, null);
    }

    public WalletResponseDTO toResponse(WalletEntity entity, WalletCardProfile cardProfile, String totpUri) {
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
                entity.getXpub() != null && !entity.getXpub().isBlank(),
                cardProfile.cardType().name(),
                cardProfile.withdrawalFeeRate(),
                cardProfile.depositFeeRate());
    }
}
