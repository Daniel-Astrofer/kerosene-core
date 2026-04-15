package source.transactions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.application.externalpayments.ExternalPaymentsLedgerPort;
import source.transactions.application.externalpayments.ExternalPaymentsMath;
import source.transactions.application.externalpayments.ExternalPaymentsNotificationPort;
import source.transactions.application.externalpayments.ExternalTransfersPort;
import source.transactions.infra.BlockchainClient;
import source.transactions.infra.CustodyGateway;
import source.transactions.model.ExternalTransferEntity;
import source.security.VaultKeyProvider;
import source.wallet.service.WalletCardProfileService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class InboundTransferMonitorService {

    private static final Logger log = LoggerFactory.getLogger(InboundTransferMonitorService.class);

    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalPaymentsLedgerPort ledgerPort;
    private final ExternalPaymentsNotificationPort notificationPort;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final WalletCardProfileService walletCardProfileService;
    private final BlockchainClient blockchainClient;
    private final CustodyGateway custodyGateway;
    private final VaultKeyProvider vaultKeyProvider;
    private final int batchSize;

    public InboundTransferMonitorService(
            ExternalTransfersPort externalTransfersPort,
            ExternalPaymentsLedgerPort ledgerPort,
            ExternalPaymentsNotificationPort notificationPort,
            ExternalPaymentsMath externalPaymentsMath,
            WalletCardProfileService walletCardProfileService,
            BlockchainClient blockchainClient,
            CustodyGateway custodyGateway,
            VaultKeyProvider vaultKeyProvider,
            @Value("${transactions.inbound-monitor.batch-size:200}") int batchSize) {
        this.externalTransfersPort = externalTransfersPort;
        this.ledgerPort = ledgerPort;
        this.notificationPort = notificationPort;
        this.externalPaymentsMath = externalPaymentsMath;
        this.walletCardProfileService = walletCardProfileService;
        this.blockchainClient = blockchainClient;
        this.custodyGateway = custodyGateway;
        this.vaultKeyProvider = vaultKeyProvider;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${transactions.inbound-monitor.fixed-delay-ms:30000}")
    public void monitorInboundTransfers() {
        if (!vaultKeyProvider.isReady()) {
            log.warn("[InboundMonitor] Skipping cycle: Master key not available yet (STALL mode).");
            return;
        }

        List<ExternalTransferEntity> pending = externalTransfersPort.findInboundTransfersForMonitoring(batchSize);
        if (pending.isEmpty()) {
            return;
        }

        for (ExternalTransferEntity transfer : pending) {
            try {
                monitorSingleTransfer(transfer);
            } catch (Exception ex) {
                log.warn("[InboundMonitor] Failed to inspect transfer {}: {}", transfer.getId(), ex.getMessage(), ex);
            }
        }
    }

    @Transactional
    public void monitorSingleTransfer(ExternalTransferEntity transfer) {
        String type = normalize(transfer.getTransferType());
        if ("INBOUND_INVOICE".equals(type)) {
            inspectLightningInvoice(transfer);
            return;
        }

        if ("ADDRESS_ISSUE".equals(type) || "ONRAMP_PURCHASE".equals(type)) {
            inspectOnchainTransfer(transfer);
        }
    }

    private void inspectOnchainTransfer(ExternalTransferEntity transfer) {
        String address = transfer.getDestination();
        if (address == null || address.isBlank()) {
            return;
        }

        long confirmedSats = blockchainClient.getConfirmedBalanceForAddress(address);
        if (confirmedSats <= 0L) {
            return;
        }

        BigDecimal grossAmount = transfer.getAmountBtc() != null && transfer.getAmountBtc().signum() > 0
                ? transfer.getAmountBtc()
                : externalPaymentsMath.satsToBtc(confirmedSats);

        completeInboundTransfer(
                transfer,
                grossAmount,
                null,
                "EXTERNAL_ONCHAIN_DEPOSIT",
                transfer.getContext() != null && transfer.getContext().contains("Onramp")
                        ? "Deposito onramp confirmado na rede Bitcoin."
                        : "Deposito on-chain confirmado na rede Bitcoin.");
    }

    private void inspectLightningInvoice(ExternalTransferEntity transfer) {
        if (transfer.getExpiresAt() != null
                && transfer.getExpiresAt().isBefore(LocalDateTime.now())
                && !"CANCELLED".equalsIgnoreCase(transfer.getStatus())) {
            transfer.setStatus("EXPIRED");
            transfer.setContext(appendContext(transfer.getContext(), "Lightning invoice expired before settlement."));
            externalTransfersPort.save(transfer);
            return;
        }

        CustodyGateway.IncomingLightningInvoiceStatus status = custodyGateway.getLightningInvoiceStatus(
                new CustodyGateway.LightningInvoiceStatusCommand(
                        transfer.getUserId(),
                        transfer.getWalletId(),
                        transfer.getWalletNameSnapshot(),
                        transfer.getExternalReference(),
                        transfer.getExternalReference(),
                        transfer.getInvoiceData()));

        String normalizedStatus = normalize(status.status());
        if (isSettledStatus(normalizedStatus)) {
            BigDecimal grossAmount = status.receivedSats() != null && status.receivedSats() > 0
                    ? externalPaymentsMath.satsToBtc(status.receivedSats())
                    : transfer.getAmountBtc();
            if (grossAmount == null || grossAmount.signum() <= 0) {
                log.warn("[InboundMonitor] Lightning transfer {} settled without amount. Skipping credit.", transfer.getId());
                return;
            }

            completeInboundTransfer(
                    transfer,
                    grossAmount,
                    transfer.getExternalReference(),
                    "EXTERNAL_LIGHTNING_DEPOSIT",
                    "Deposito Lightning liquidado com sucesso.");
            return;
        }

        if (isCancelledStatus(normalizedStatus) && !"CANCELLED".equalsIgnoreCase(transfer.getStatus())) {
            transfer.setStatus("CANCELLED");
            transfer.setContext(appendContext(transfer.getContext(), "Lightning invoice cancelled by provider."));
            externalTransfersPort.save(transfer);
            return;
        }

        if (isExpiredStatus(normalizedStatus) && !"EXPIRED".equalsIgnoreCase(transfer.getStatus())) {
            transfer.setStatus("EXPIRED");
            transfer.setContext(appendContext(transfer.getContext(), "Lightning invoice expired on provider."));
            externalTransfersPort.save(transfer);
        }
    }

    private void completeInboundTransfer(
            ExternalTransferEntity transfer,
            BigDecimal grossAmount,
            String blockchainReference,
            String historyType,
            String notificationBody) {
        if (grossAmount == null || grossAmount.signum() <= 0) {
            return;
        }

        BigDecimal normalizedGross = externalPaymentsMath.normalizeBtc(grossAmount);
        BigDecimal depositFee = walletCardProfileService.calculateDepositFee(
                transfer.getUserId(),
                normalizedGross);
        BigDecimal netCredit = externalPaymentsMath.normalizeBtc(normalizedGross.subtract(depositFee));
        if (netCredit.signum() <= 0) {
            log.warn("[InboundMonitor] Transfer {} net credit is non-positive after fee. Skipping.", transfer.getId());
            return;
        }

        ledgerPort.updateBalance(
                transfer.getWalletId(),
                netCredit,
                "INBOUND_TRANSFER:" + transfer.getId());
        ledgerPort.recordHistory(new ExternalPaymentsLedgerPort.HistoryRecord(
                transfer.getUserId(),
                transfer.getProvider(),
                transfer.getWalletNameSnapshot(),
                historyType,
                netCredit,
                BigDecimal.ZERO,
                "COMPLETED",
                blockchainReference,
                buildCompletionContext(transfer, normalizedGross, depositFee, netCredit),
                LocalDateTime.now()));

        transfer.setAmountBtc(normalizedGross);
        transfer.setPlatformFeeBtc(depositFee);
        transfer.setTotalDebitedBtc(normalizedGross);
        transfer.setStatus("COMPLETED");
        transfer.setContext(appendContext(
                transfer.getContext(),
                "Inbound transfer settled and credited. gross=" + normalizedGross.toPlainString()
                        + " BTC | fee=" + depositFee.toPlainString()
                        + " BTC | net=" + netCredit.toPlainString() + " BTC"));
        externalTransfersPort.save(transfer);

        notificationPort.notifyUser(
                transfer.getUserId(),
                "Deposito confirmado",
                notificationBody + " Liquido creditado: " + netCredit.toPlainString() + " BTC.");
    }

    private String buildCompletionContext(
            ExternalTransferEntity transfer,
            BigDecimal grossAmount,
            BigDecimal depositFee,
            BigDecimal netCredit) {
        String prefix = normalize(transfer.getTransferType()).equals("INBOUND_INVOICE")
                ? "Lightning Deposit"
                : "On-Chain Deposit";
        return prefix + " | gross=" + grossAmount.toPlainString()
                + " BTC | fee=" + depositFee.toPlainString()
                + " BTC | net=" + netCredit.toPlainString() + " BTC";
    }

    private boolean isSettledStatus(String status) {
        return "SETTLED".equals(status)
                || "PAID".equals(status)
                || "COMPLETED".equals(status)
                || "CONFIRMED".equals(status);
    }

    private boolean isCancelledStatus(String status) {
        return "CANCELLED".equals(status)
                || "CANCELED".equals(status);
    }

    private boolean isExpiredStatus(String status) {
        return "EXPIRED".equals(status)
                || "VOID".equals(status);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String appendContext(String currentContext, String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return currentContext;
        }
        if (currentContext == null || currentContext.isBlank()) {
            return suffix;
        }
        if (currentContext.contains(suffix)) {
            return currentContext;
        }
        return currentContext + " | " + suffix;
    }
}
