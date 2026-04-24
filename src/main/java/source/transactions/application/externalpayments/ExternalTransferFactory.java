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
    private final String bitcoinNetwork;

    public ExternalTransferFactory(ExternalPaymentsMath externalPaymentsMath) {
        this.externalPaymentsMath = externalPaymentsMath;
        this.bitcoinNetwork = externalPaymentsMath.configuredBitcoinNetwork();
    }

    public ExternalTransferEntity newTransfer(
            WalletEntity wallet,
            String network,
            String transferType,
            String status,
            String provider,
            String destination,
            String externalReference,
            String invoiceId,
            String blockchainTxid,
            String paymentHash,
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
        transfer.setInvoiceId(invoiceId);
        transfer.setBlockchainTxid(blockchainTxid);
        transfer.setPaymentHash(paymentHash);
        transfer.setInvoiceData(invoiceData);
        transfer.setAmountBtc(externalPaymentsMath.nullableNormalizeBtc(amountBtc));
        transfer.setNetworkFeeBtc(externalPaymentsMath.nullableNormalizeBtc(networkFeeBtc));
        transfer.setPlatformFeeBtc(externalPaymentsMath.nullableNormalizeBtc(platformFeeBtc));
        transfer.setTotalDebitedBtc(externalPaymentsMath.nullableNormalizeBtc(totalDebitedBtc));
        transfer.setExpiresAt(expiresAt);
        transfer.setContext(context);
        return transfer;
    }

    public WalletNetworkAddressDTO toWalletNetworkAddress(
            WalletEntity wallet,
            String provider,
            boolean lightningEnabled,
            String lightningUnavailableReason) {
        return new WalletNetworkAddressDTO(
                wallet.getName(),
                wallet.getDepositAddress(),
                wallet.getLightningAddress(),
                bitcoinNetwork,
                provider,
                wallet.getExternalWalletReference(),
                wallet.getWalletMode().name(),
                lightningEnabled,
                lightningUnavailableReason);
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
                entity.getInvoiceId(),
                entity.getBlockchainTxid(),
                entity.getPaymentHash(),
                entity.getInvoiceData(),
                entity.getAmountBtc(),
                entity.getNetworkFeeBtc(),
                entity.getPlatformFeeBtc(),
                entity.getTotalDebitedBtc(),
                entity.getExternalReference(),
                entity.getConfirmations(),
                entity.getExpiresAt(),
                entity.getDetectedAt(),
                entity.getSettledAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getContext());
    }
}
