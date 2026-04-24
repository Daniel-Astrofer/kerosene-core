package source.transactions.application.externalpayments;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.dto.OnchainAddressAllocationDTO;
import source.transactions.dto.OnchainAddressRequestDTO;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.service.BlockchainAddressWatchService;
import source.transactions.service.CustodialAddressAllocator;
import source.wallet.model.WalletEntity;

@Service
public class IssueOnchainAddressUseCase {

    private final ExternalPaymentsWalletPort walletPort;
    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalTransferFactory externalTransferFactory;
    private final CustodialAddressAllocator custodialAddressAllocator;
    private final BlockchainAddressWatchService blockchainAddressWatchService;
    private final String bitcoinNetwork;
    private final int minimumConfirmations;

    public IssueOnchainAddressUseCase(
            ExternalPaymentsWalletPort walletPort,
            ExternalTransfersPort externalTransfersPort,
            ExternalTransferFactory externalTransferFactory,
            CustodialAddressAllocator custodialAddressAllocator,
            BlockchainAddressWatchService blockchainAddressWatchService,
            @org.springframework.beans.factory.annotation.Value("${bitcoin.network:testnet}") String bitcoinNetwork,
            @org.springframework.beans.factory.annotation.Value("${bitcoin.min-confirmations:3}") int minimumConfirmations) {
        this.walletPort = walletPort;
        this.externalTransfersPort = externalTransfersPort;
        this.externalTransferFactory = externalTransferFactory;
        this.custodialAddressAllocator = custodialAddressAllocator;
        this.blockchainAddressWatchService = blockchainAddressWatchService;
        this.bitcoinNetwork = normalizeNetwork(bitcoinNetwork);
        this.minimumConfirmations = Math.max(1, minimumConfirmations);
    }

    @Transactional
    public OnchainAddressAllocationDTO issue(Long userId, OnchainAddressRequestDTO request) {
        WalletEntity wallet = walletPort.requireWallet(userId, request.walletName());
        boolean regenerate = Boolean.TRUE.equals(request.regenerate());

        CustodialAddressAllocator.Allocation allocation = custodialAddressAllocator.allocate(
                userId,
                wallet,
                "wallet:" + wallet.getName(),
                regenerate);

        ExternalTransferEntity transfer = externalTransfersPort.save(externalTransferFactory.newTransfer(
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
                null,
                null,
                null,
                "On-chain deposit address issued for wallet " + wallet.getName()));
        blockchainAddressWatchService.register(transfer, allocation.address(), "wallet:" + wallet.getName());

        return new OnchainAddressAllocationDTO(
                wallet.getName(),
                allocation.address(),
                bitcoinNetwork,
                allocation.provider(),
                allocation.externalReference(),
                wallet.getWalletMode().name(),
                transfer.getId(),
                transfer.getStatus(),
                transfer.getConfirmations(),
                minimumConfirmations,
                transfer.getBlockchainTxid());
    }

    private String normalizeNetwork(String value) {
        if (value == null || value.isBlank()) {
            return "testnet";
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
