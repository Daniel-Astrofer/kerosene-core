package source.transactions.application.externalpayments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import source.transactions.dto.ExternalTransferResponseDTO;
import source.transactions.dto.WalletNetworkAddressDTO;
import source.transactions.exception.ExternalPaymentsExceptions;
import source.transactions.infra.LightningInvoiceGateway;
import source.transactions.model.ExternalTransferEntity;
import source.wallet.model.WalletEntity;

import java.util.List;
import java.util.UUID;

@Service
public class ExternalPaymentsQueryService {

    private final ExternalPaymentsWalletPort walletPort;
    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalTransferFactory externalTransferFactory;
    private final LightningInvoiceGateway lightningInvoiceGateway;
    private final String localAddressProviderName;

    public ExternalPaymentsQueryService(
            ExternalPaymentsWalletPort walletPort,
            ExternalTransfersPort externalTransfersPort,
            ExternalTransferFactory externalTransferFactory,
            @Qualifier("externalLightningInvoiceGateway")
            LightningInvoiceGateway lightningInvoiceGateway,
            @Value("${transactions.local-address-provider-name:KEROSENE_LOCAL}") String localAddressProviderName) {
        this.walletPort = walletPort;
        this.externalTransfersPort = externalTransfersPort;
        this.externalTransferFactory = externalTransferFactory;
        this.lightningInvoiceGateway = lightningInvoiceGateway;
        this.localAddressProviderName = localAddressProviderName;
    }

    public WalletNetworkAddressDTO getWalletNetworkProfile(Long userId, String walletName) {
        WalletEntity wallet = walletPort.requireWallet(userId, walletName);
        return externalTransferFactory.toWalletNetworkAddress(
                wallet,
                resolveProviderName(wallet),
                wallet.isKeroseneCustodyMode() && lightningInvoiceGateway.isLive(),
                lightningUnavailableReason(wallet));
    }

    public List<ExternalTransferResponseDTO> listTransfers(Long userId) {
        return externalTransfersPort.listByUserId(userId).stream()
                .map(externalTransferFactory::toResponseDTO)
                .toList();
    }

    public ExternalTransferResponseDTO getTransfer(Long userId, UUID transferId) {
        ExternalTransferEntity transfer = externalTransfersPort.findByIdAndUserId(transferId, userId)
                .orElseThrow(() -> new ExternalPaymentsExceptions.TransferNotFound(
                        "The requested external transfer could not be found."));
        return externalTransferFactory.toResponseDTO(transfer);
    }

    private String resolveProviderName(WalletEntity wallet) {
        if (wallet != null && wallet.isSelfCustodyMode()) {
            return "SELF_CUSTODY_XPUB";
        }
        if (lightningInvoiceGateway.isLive() && lightningInvoiceGateway.providerName() != null) {
            return lightningInvoiceGateway.providerName();
        }
        return localAddressProviderName;
    }

    private String lightningUnavailableReason(WalletEntity wallet) {
        if (wallet != null && wallet.isSelfCustodyMode()) {
            return "Lightning invoices are only available for KEROSENE custodial wallets.";
        }
        if (lightningInvoiceGateway.isLive()) {
            return null;
        }
        return "Lightning indisponível porque o backend não conseguiu estabelecer a sessão gRPC com o LND.";
    }
}
