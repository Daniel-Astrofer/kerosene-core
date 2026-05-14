package source.transactions.application.externalpayments;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import source.transactions.dto.OnchainAddressAllocationDTO;
import source.transactions.dto.OnchainAddressRequestDTO;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.service.BlockchainAddressWatchService;
import source.transactions.service.CustodialAddressAllocator;
import source.transactions.service.NetworkTransferLifecycleService;
import source.wallet.model.WalletEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class IssueOnchainAddressUseCase {

    private static final Logger log = LoggerFactory.getLogger(IssueOnchainAddressUseCase.class);
    private static final BigDecimal SATOSHIS_PER_BTC = new BigDecimal("100000000");

    private final ExternalPaymentsWalletPort walletPort;
    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalTransferFactory externalTransferFactory;
    private final CustodialAddressAllocator custodialAddressAllocator;
    private final BlockchainAddressWatchService blockchainAddressWatchService;
    private final NetworkTransferLifecycleService networkTransferLifecycleService;
    private final String bitcoinNetwork;
    private final int minimumConfirmations;
    private final boolean instantSettlementTestModeEnabled;

    public IssueOnchainAddressUseCase(
            ExternalPaymentsWalletPort walletPort,
            ExternalTransfersPort externalTransfersPort,
            ExternalTransferFactory externalTransferFactory,
            CustodialAddressAllocator custodialAddressAllocator,
            BlockchainAddressWatchService blockchainAddressWatchService,
            NetworkTransferLifecycleService networkTransferLifecycleService,
            @org.springframework.beans.factory.annotation.Value("${bitcoin.network:mainnet}") String bitcoinNetwork,
            @org.springframework.beans.factory.annotation.Value("${bitcoin.min-confirmations:3}") int minimumConfirmations,
            @org.springframework.beans.factory.annotation.Value("${transactions.onchain.test-instant-settlement-enabled:false}") boolean instantSettlementTestModeEnabled) {
        this.walletPort = walletPort;
        this.externalTransfersPort = externalTransfersPort;
        this.externalTransferFactory = externalTransferFactory;
        this.custodialAddressAllocator = custodialAddressAllocator;
        this.blockchainAddressWatchService = blockchainAddressWatchService;
        this.networkTransferLifecycleService = networkTransferLifecycleService;
        this.bitcoinNetwork = normalizeNetwork(bitcoinNetwork);
        this.minimumConfirmations = Math.max(1, minimumConfirmations);
        this.instantSettlementTestModeEnabled = instantSettlementTestModeEnabled;
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
                        + " expected=" + expectedAmountBtc.toPlainString() + " BTC"
                        + (instantSettlementTestModeEnabled
                                ? " | LOCAL TEST: credited without blockchain observation"
                                : ""));
        transfer.setExpectedAmountBtc(expectedAmountBtc);
        transfer = externalTransfersPort.save(transfer);
        if (instantSettlementTestModeEnabled) {
            transfer = settleImmediatelyForLocalTest(transfer, expectedAmountBtc);
        } else {
            blockchainAddressWatchService.register(transfer, allocation.address(), "deposit:" + wallet.getName());
        }

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

    private ExternalTransferEntity settleImmediatelyForLocalTest(
            ExternalTransferEntity transfer,
            BigDecimal expectedAmountBtc) {
        long amountSats = expectedAmountBtc.multiply(SATOSHIS_PER_BTC)
                .setScale(0, RoundingMode.DOWN)
                .longValue();
        String syntheticTxid = "local-onchain-test-" + transfer.getId();
        log.warn("[OnchainDepositTestMode] Crediting transfer {} without blockchain observation. "
                        + "This must stay disabled outside local app testing.",
                transfer.getId());
        ExternalTransferEntity settled = networkTransferLifecycleService.reconcileOnchainSettlement(
                transfer,
                amountSats,
                syntheticTxid,
                minimumConfirmations,
                "ONCHAIN_TEST_INSTANT_SETTLEMENT");
        return settled != null ? settled : transfer;
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
