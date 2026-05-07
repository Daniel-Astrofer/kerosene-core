package source.transactions.application.externalpayments;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.dto.OnchainAddressAllocationDTO;
import source.transactions.dto.OnchainAddressRequestDTO;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.service.BlockchainAddressWatchService;
import source.transactions.service.CustodialAddressAllocator;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;

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
            @org.springframework.beans.factory.annotation.Value("${bitcoin.network:mainnet}") String bitcoinNetwork,
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
        if (!wallet.isKeroseneCustodyMode()) {
            throw new IllegalArgumentException(
                    "On-chain deposits into a Kerosene account require a KEROSENE custodial wallet.");
        }
        BigDecimal expectedAmountBtc = normalizeExpectedAmount(request.expectedAmountBtc());

        CustodialAddressAllocator.Allocation allocation = custodialAddressAllocator.allocate(
                userId,
                wallet,
                "deposit:" + wallet.getName(),
                true);

        ExternalTransferEntity transfer = externalTransferFactory.newTransfer(
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
                "On-chain deposit address issued for wallet " + wallet.getName()
                        + " expected=" + expectedAmountBtc.toPlainString() + " BTC");
        transfer.setExpectedAmountBtc(expectedAmountBtc);
        transfer = externalTransfersPort.save(transfer);
        blockchainAddressWatchService.register(transfer, allocation.address(), "deposit:" + wallet.getName());

        return new OnchainAddressAllocationDTO(
                wallet.getName(),
                allocation.address(),
                expectedAmountBtc,
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

    private BigDecimal normalizeExpectedAmount(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("expectedAmountBtc is required and must be positive.");
        }
        BigDecimal normalized = value.setScale(8, java.math.RoundingMode.DOWN);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("expectedAmountBtc is below the minimum Bitcoin precision.");
        }
        return normalized;
    }

    private String normalizeNetwork(String value) {
        if (value == null || value.isBlank()) {
            return "mainnet";
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
