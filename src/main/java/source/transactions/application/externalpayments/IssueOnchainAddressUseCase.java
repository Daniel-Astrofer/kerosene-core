package source.transactions.application.externalpayments;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.dto.OnchainAddressRequestDTO;
import source.transactions.dto.WalletNetworkAddressDTO;
import source.transactions.service.CustodialAddressAllocator;
import source.wallet.model.WalletEntity;

@Service
public class IssueOnchainAddressUseCase {

    private final ExternalPaymentsWalletPort walletPort;
    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalTransferFactory externalTransferFactory;
    private final CustodialAddressAllocator custodialAddressAllocator;

    public IssueOnchainAddressUseCase(
            ExternalPaymentsWalletPort walletPort,
            ExternalTransfersPort externalTransfersPort,
            ExternalTransferFactory externalTransferFactory,
            CustodialAddressAllocator custodialAddressAllocator) {
        this.walletPort = walletPort;
        this.externalTransfersPort = externalTransfersPort;
        this.externalTransferFactory = externalTransferFactory;
        this.custodialAddressAllocator = custodialAddressAllocator;
    }

    @Transactional
    public WalletNetworkAddressDTO issue(Long userId, OnchainAddressRequestDTO request) {
        WalletEntity wallet = walletPort.requireWallet(userId, request.walletName());
        boolean regenerate = Boolean.TRUE.equals(request.regenerate());

        CustodialAddressAllocator.Allocation allocation = custodialAddressAllocator.allocate(
                userId,
                wallet,
                "wallet:" + wallet.getName(),
                regenerate);

        externalTransfersPort.save(externalTransferFactory.newTransfer(
                wallet,
                "ONCHAIN",
                "ADDRESS_ISSUE",
                "PENDING",
                allocation.provider(),
                allocation.address(),
                allocation.externalReference(),
                null,
                null,
                null,
                null,
                null,
                null,
                "On-chain deposit address issued for wallet " + wallet.getName()));

        wallet = walletPort.requireWallet(userId, request.walletName());
        return externalTransferFactory.toWalletNetworkAddress(wallet, allocation.provider());
    }
}
