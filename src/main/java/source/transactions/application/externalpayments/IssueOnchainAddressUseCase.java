package source.transactions.application.externalpayments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.dto.OnchainAddressRequestDTO;
import source.transactions.dto.WalletNetworkAddressDTO;
import source.transactions.infra.CustodyGateway;
import source.wallet.model.WalletEntity;

@Service
public class IssueOnchainAddressUseCase {

    private final ExternalPaymentsWalletPort walletPort;
    private final ExternalTransfersPort externalTransfersPort;
    private final CustodyGateway custodyGateway;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final ExternalTransferFactory externalTransferFactory;
    private final String localAddressProviderName;

    public IssueOnchainAddressUseCase(
            ExternalPaymentsWalletPort walletPort,
            ExternalTransfersPort externalTransfersPort,
            CustodyGateway custodyGateway,
            ExternalPaymentsMath externalPaymentsMath,
            ExternalTransferFactory externalTransferFactory,
            @Value("${transactions.local-address-provider-name:KEROSENE_LOCAL}") String localAddressProviderName) {
        this.walletPort = walletPort;
        this.externalTransfersPort = externalTransfersPort;
        this.custodyGateway = custodyGateway;
        this.externalPaymentsMath = externalPaymentsMath;
        this.externalTransferFactory = externalTransferFactory;
        this.localAddressProviderName = localAddressProviderName;
    }

    @Transactional
    public WalletNetworkAddressDTO issue(Long userId, OnchainAddressRequestDTO request) {
        WalletEntity wallet = walletPort.requireWallet(userId, request.walletName());
        boolean regenerate = Boolean.TRUE.equals(request.regenerate());

        if (!regenerate && wallet.getDepositAddress() != null && !wallet.getDepositAddress().isBlank()) {
            return externalTransferFactory.toWalletNetworkAddress(wallet, resolveProviderName());
        }

        String address;
        String externalReference;
        String provider = resolveProviderName();

        if (custodyGateway.isLive()) {
            CustodyGateway.GeneratedOnchainAddress issued = custodyGateway.createOnchainAddress(
                    new CustodyGateway.OnchainAddressCommand(userId, wallet.getId(), wallet.getName(),
                            "wallet:" + wallet.getName()));
            address = issued.address();
            externalReference = externalPaymentsMath.firstNonBlank(issued.walletReference(), issued.providerReference());
        } else if (wallet.getXpub() != null && !wallet.getXpub().isBlank()) {
            int index = walletPort.incrementLastDerivedIndex(wallet.getId());
            address = walletPort.deriveAddressFromXpub(wallet.getXpub(), index);
            externalReference = "XPUB_INDEX_" + index;
            provider = localAddressProviderName;
        } else {
            address = walletPort.deriveAddress(wallet.getId(), wallet.getPassphraseHash());
            externalReference = "STATIC_DERIVATION";
            provider = localAddressProviderName;
        }

        wallet.setDepositAddress(address);
        wallet.setExternalWalletReference(externalReference);
        walletPort.save(wallet);

        externalTransfersPort.save(externalTransferFactory.newTransfer(
                wallet,
                "ONCHAIN",
                "ADDRESS_ISSUE",
                "PENDING",
                provider,
                address,
                externalReference,
                null,
                null,
                null,
                null,
                null,
                null,
                "On-chain deposit address issued for wallet " + wallet.getName()));

        return externalTransferFactory.toWalletNetworkAddress(wallet, provider);
    }

    private String resolveProviderName() {
        return custodyGateway.providerName() != null ? custodyGateway.providerName() : localAddressProviderName;
    }
}
