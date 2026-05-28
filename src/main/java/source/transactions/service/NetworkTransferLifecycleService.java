package source.transactions.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.application.externalpayments.ExternalPaymentsMath;
import source.transactions.application.externalpayments.ExternalTransfersPort;
import source.transactions.model.ExternalTransferEntity;

import java.time.LocalDateTime;

@Service
public class NetworkTransferLifecycleService {

    private final ExternalTransfersPort externalTransfersPort;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final ExternalInboundSettlementService inboundSettlementService;
    private final NetworkTransferEventService networkTransferEventService;
    private final int minimumConfirmations;

    public NetworkTransferLifecycleService(
            ExternalTransfersPort externalTransfersPort,
            ExternalPaymentsMath externalPaymentsMath,
            ExternalInboundSettlementService inboundSettlementService,
            NetworkTransferEventService networkTransferEventService,
            @Value("${bitcoin.min-confirmations:3}") int minimumConfirmations) {
        this.externalTransfersPort = externalTransfersPort;
        this.externalPaymentsMath = externalPaymentsMath;
        this.inboundSettlementService = inboundSettlementService;
        this.networkTransferEventService = networkTransferEventService;
        this.minimumConfirmations = minimumConfirmations;
    }

    @Transactional
    public ExternalTransferEntity markOnchainSeen(
            ExternalTransferEntity transfer,
            String txid,
            long amountSats,
            int confirmations,
            String source) {
        transfer.setBlockchainTxid(txid);
        transfer.setConfirmations(Math.max(0, confirmations));
        transfer.setDetectedAt(transfer.getDetectedAt() != null ? transfer.getDetectedAt() : LocalDateTime.now());
        transfer.setAmountBtc(
                transfer.getAmountBtc() != null && transfer.getAmountBtc().signum() > 0
                        ? transfer.getAmountBtc()
                        : externalPaymentsMath.satsToBtc(amountSats));
        if (confirmations >= minimumConfirmations) {
            transfer.setStatus("CONFIRMED");
        } else {
            transfer.setStatus("DETECTED");
        }
        transfer = externalTransfersPort.save(transfer);
        networkTransferEventService.info(
                transfer,
                confirmations >= minimumConfirmations ? "ONCHAIN_CONFIRMED" : "ONCHAIN_DETECTED",
                txid,
                "source=" + source + " | amountSats=" + amountSats + " | confirmations=" + confirmations);
        return transfer;
    }

    @Transactional
    public ExternalTransferEntity reconcileOnchainSettlement(
            ExternalTransferEntity transfer,
            long amountSats,
            String txid,
            int confirmations,
            String source) {
        ExternalTransferEntity updated = markOnchainSeen(transfer, txid, amountSats, confirmations, source);
        if (confirmations >= minimumConfirmations
                && updated.getTransferType() != null
                && !"OUTBOUND_PAYMENT".equalsIgnoreCase(updated.getTransferType())) {
            boolean settled = inboundSettlementService.settleOnchainInbound(
                    updated,
                    amountSats,
                    txid,
                    confirmations,
                    "Deposito on-chain confirmado via " + source + ".");
            if (settled || isAlreadyCompleted(updated)) {
                updated.setStatus("COMPLETED");
                updated.setSettledAt(updated.getSettledAt() != null ? updated.getSettledAt() : LocalDateTime.now());
                updated = externalTransfersPort.save(updated);
            }
        }
        return updated;
    }

    @Transactional
    public ExternalTransferEntity reconcileLightningInvoice(
            ExternalTransferEntity transfer,
            String status,
            long receivedSats,
            String paymentHash,
            String payload,
            String source) {
        transfer.setProviderPayload(payload);
        transfer.setPaymentHash(paymentHash != null ? paymentHash : transfer.getPaymentHash());
        transfer.setDetectedAt(transfer.getDetectedAt() != null ? transfer.getDetectedAt() : LocalDateTime.now());

        String normalizedStatus = status != null ? status.trim().toUpperCase() : "";
        if (isSettledLightningStatus(normalizedStatus)) {
            boolean settled = inboundSettlementService.settleLightningInbound(
                    transfer,
                    receivedSats,
                    paymentHash != null ? paymentHash : transfer.getPaymentHash(),
                    "Deposito Lightning liquidado via " + source + ".");
            if (settled || isAlreadyCompleted(transfer)) {
                transfer.setStatus("COMPLETED");
                transfer.setSettledAt(transfer.getSettledAt() != null ? transfer.getSettledAt() : LocalDateTime.now());
            } else if (!"AUTO_RESOLUTION_PENDING".equalsIgnoreCase(transfer.getStatus())) {
                transfer.setStatus(normalizedStatus);
            }
        } else if ("EXPIRED".equals(normalizedStatus) || "INVALID".equals(normalizedStatus)) {
            transfer.setStatus("EXPIRED");
        } else if ("CANCELLED".equals(normalizedStatus)) {
            transfer.setStatus("CANCELLED");
        } else if (!normalizedStatus.isBlank()) {
            transfer.setStatus(normalizedStatus);
        }

        transfer = externalTransfersPort.save(transfer);
        networkTransferEventService.info(
                transfer,
                "LIGHTNING_RECONCILED",
                paymentHash != null ? paymentHash : transfer.getInvoiceId(),
                "source=" + source + " | status=" + transfer.getStatus() + " | receivedSats=" + receivedSats);
        return transfer;
    }

    @Transactional
    public ExternalTransferEntity expireLightningInvoice(ExternalTransferEntity transfer, String source) {
        if (transfer == null) {
            return null;
        }
        if (isAlreadyCompleted(transfer)) {
            return transfer;
        }
        transfer.setStatus("EXPIRED");
        transfer.setContext(appendContext(
                transfer.getContext(),
                "Lightning invoice expired before settlement. source=" + source));
        transfer = externalTransfersPort.save(transfer);
        networkTransferEventService.info(
                transfer,
                "LIGHTNING_INVOICE_EXPIRED",
                transfer.getPaymentHash() != null ? transfer.getPaymentHash() : transfer.getInvoiceId(),
                "source=" + source);
        return transfer;
    }

    private boolean isSettledLightningStatus(String status) {
        return "SETTLED".equals(status)
                || "COMPLETED".equals(status)
                || "PAID".equals(status)
                || "CONFIRMED".equals(status);
    }

    private boolean isAlreadyCompleted(ExternalTransferEntity transfer) {
        return transfer != null
                && ("COMPLETED".equalsIgnoreCase(transfer.getStatus()) || transfer.getSettledAt() != null);
    }

    private String appendContext(String current, String addition) {
        if (addition == null || addition.isBlank()) {
            return current;
        }
        if (current == null || current.isBlank()) {
            return addition;
        }
        if (current.contains(addition)) {
            return current;
        }
        return current + " | " + addition;
    }
}
