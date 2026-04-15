package source.transactions.application.externalpayments;

import org.springframework.stereotype.Component;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.dto.WalletNetworkAddressDTO;
import source.transactions.model.ExternalTransferEntity;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;

@Component
public class ExternalTransferFactory {

    private final ExternalPaymentsMath externalPaymentsMath;

    public ExternalTransferFactory(ExternalPaymentsMath externalPaymentsMath) {
        this.externalPaymentsMath = externalPaymentsMath;
    }

    public ExternalTransferEntity newTransfer(
            WalletEntity wallet,
            String network,
            String transferType,
            String status,
            String provider,
            String destination,
            String externalReference,
            String invoiceData,
            BigDecimal amountBtc,
            BigDecimal networkFeeBtc,
            BigDecimal platformFeeBtc,
            BigDecimal totalDebitedBtc,
            java.time.LocalDateTime expiresAt,
            String context) {
        ExternalTransferEntity transfer = new ExternalTransferEntity();
        transfer.setUserId(wallet.getUser().getId());
        transfer.setWalletId(wallet.getId());
        transfer.setWalletNameSnapshot(wallet.getName());
        transfer.setNetwork(network);
        transfer.setTransferType(transferType);
        transfer.setStatus(status);
        transfer.setProvider(provider);
        transfer.setDestination(destination);
        transfer.setExternalReference(externalReference);
        transfer.setInvoiceData(invoiceData);
        transfer.setAmountBtc(externalPaymentsMath.nullableNormalizeBtc(amountBtc));
        transfer.setNetworkFeeBtc(externalPaymentsMath.nullableNormalizeBtc(networkFeeBtc));
        transfer.setPlatformFeeBtc(externalPaymentsMath.nullableNormalizeBtc(platformFeeBtc));
        transfer.setTotalDebitedBtc(externalPaymentsMath.nullableNormalizeBtc(totalDebitedBtc));
        transfer.setExpiresAt(expiresAt);
        transfer.setContext(context);
        return transfer;
    }

    public WalletNetworkAddressDTO toWalletNetworkAddress(WalletEntity wallet, String provider) {
        return new WalletNetworkAddressDTO(
                wallet.getName(),
                wallet.getDepositAddress(),
                wallet.getLightningAddress(),
                provider,
                wallet.getExternalWalletReference());
    }

    public ExternalTransferResponseDTO toResponseDTO(ExternalTransferEntity entity) {
        return new ExternalTransferResponseDTO(
                entity.getId(),
                entity.getNetwork(),
                entity.getTransferType(),
                entity.getStatus(),
                entity.getProvider(),
                entity.getWalletNameSnapshot(),
                entity.getDestination(),
                entity.getAmountBtc(),
                entity.getNetworkFeeBtc(),
                entity.getPlatformFeeBtc(),
                entity.getTotalDebitedBtc(),
                entity.getExternalReference(),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getContext());
    }
}
