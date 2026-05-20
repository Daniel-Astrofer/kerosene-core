package source.transactions.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.observability.FinancialOperationsMetrics;
import source.ledger.exceptions.LedgerExceptions;
import source.transactions.application.externalpayments.ExternalPaymentsLedgerPort;
import source.transactions.infra.BlockchainClient;
import source.transactions.model.ExternalProviderOutboxEntity;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.model.FinancialReconciliationIssueEntity;
import source.transactions.model.FinancialReconciliationRunEntity;
import source.transactions.repository.ExternalTransferRepository;
import source.transactions.repository.FinancialReconciliationIssueRepository;
import source.transactions.repository.FinancialReconciliationRunRepository;
import source.treasury.service.FinancialAuditTrailService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FinancialReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(FinancialReconciliationService.class);
    private static final List<String> RECONCILABLE_STATUSES = List.of(
            "PROVIDER_PENDING",
            "PROVIDER_FAILED",
            "DETECTED",
            "CONFIRMED",
            "COMPLETED",
            "AUTO_RESOLUTION_PENDING",
            "FAILED_SAFE");

    private final ExternalTransferRepository externalTransferRepository;
    private final FinancialReconciliationRunRepository runRepository;
    private final FinancialReconciliationIssueRepository issueRepository;
    private final ExternalProviderOutboxService outboxService;
    private final NetworkTransferEventService eventService;
    private final BlockchainClient blockchainClient;
    private final FinancialAuditTrailService auditTrailService;
    private final FinancialOperationsMetrics metrics;
    private final ExternalPaymentsLedgerPort ledgerPort;
    private final ProcessedTransactionService processedTransactionService;

    public FinancialReconciliationService(
            ExternalTransferRepository externalTransferRepository,
            FinancialReconciliationRunRepository runRepository,
            FinancialReconciliationIssueRepository issueRepository,
            ExternalProviderOutboxService outboxService,
            NetworkTransferEventService eventService,
            BlockchainClient blockchainClient,
            FinancialAuditTrailService auditTrailService,
            FinancialOperationsMetrics metrics,
            ExternalPaymentsLedgerPort ledgerPort,
            ProcessedTransactionService processedTransactionService) {
        this.externalTransferRepository = externalTransferRepository;
        this.runRepository = runRepository;
        this.issueRepository = issueRepository;
        this.outboxService = outboxService;
        this.eventService = eventService;
        this.blockchainClient = blockchainClient;
        this.auditTrailService = auditTrailService;
        this.metrics = metrics;
        this.ledgerPort = ledgerPort;
        this.processedTransactionService = processedTransactionService;
    }

    @Scheduled(fixedDelayString = "${financial.reconciliation.fixed-delay-ms:60000}")
    public void scheduledReconciliation() {
        try {
            runOnce();
        } catch (RuntimeException exception) {
            log.error("[FinancialReconciliation] Failed to run reconciliation: {}", exception.getMessage());
            metrics.increment("reconciliation", "failed");
        }
    }

    @Transactional
    public FinancialReconciliationRunEntity runOnce() {
        FinancialReconciliationRunEntity run = runRepository.save(new FinancialReconciliationRunEntity());
        int checked = 0;
        int issues = 0;

        for (ExternalTransferEntity transfer : externalTransferRepository
                .findTop200ByStatusInOrderByUpdatedAtAsc(RECONCILABLE_STATUSES)) {
            checked++;
            issues += inspectTransfer(run, transfer);
        }

        for (ExternalProviderOutboxEntity outbox : outboxService.findDueForAutomaticResolution()) {
            issues++;
            recordIssue(
                    run,
                    outbox.getTransferId(),
                    "PROVIDER_OUTBOX_NOT_DISPATCHED",
                    "HIGH",
                    outbox.getIdempotencyKey(),
                    "Provider outbox item is due for automatic retry and not dispatched. status=" + outbox.getStatus()
                            + " attempts=" + outbox.getAttempts());
        }

        run.setCheckedTransfers(checked);
        run.setIssueCount(issues);
        run.setStatus(issues > 0 ? "ISSUES_FOUND" : "OK");
        run.setSummary("checkedTransfers=" + checked + " issueCount=" + issues);
        run.setFinishedAt(LocalDateTime.now());
        FinancialReconciliationRunEntity saved = runRepository.save(run);
        auditTrailService.recordBestEffort(
                "FINANCIAL_RECONCILIATION_RUN",
                "RECONCILIATION",
                saved.getId().toString(),
                null,
                saved.getStatus(),
                Map.of(
                        "checkedTransfers", checked,
                        "issueCount", issues,
                        "status", saved.getStatus()));
        metrics.increment("reconciliation", saved.getStatus().toLowerCase());
        return saved;
    }

    private int inspectTransfer(FinancialReconciliationRunEntity run, ExternalTransferEntity transfer) {
        int issues = 0;
        if (transfer.getBlockchainTxid() != null && !transfer.getBlockchainTxid().isBlank()) {
            JsonNode tx = lookupRawTransaction(transfer);
            if (tx != null) {
                int observedConfirmations = confirmations(tx);
                int storedConfirmations = transfer.getConfirmations() != null ? transfer.getConfirmations() : 0;
                if (observedConfirmations < storedConfirmations) {
                    issues += inspectConfirmationRegression(run, transfer, observedConfirmations);
                }
            }
        }

        if ("PROVIDER_FAILED".equalsIgnoreCase(transfer.getStatus())) {
            issues += inspectProviderFailure(run, transfer);
        } else if (staleProviderPending(transfer)) {
            issues += inspectStaleProviderPending(run, transfer);
        }
        return issues;
    }

    private JsonNode lookupRawTransaction(ExternalTransferEntity transfer) {
        try {
            return blockchainClient.getRawTransaction(transfer.getBlockchainTxid(), true);
        } catch (RuntimeException exception) {
            if (isPrunedTransactionLookupFailure(exception)) {
                log.debug(
                        "[FinancialReconciliation] Transaction {} is not available from the current Bitcoin Core node; "
                                + "skipping confirmation-regression check for transfer {}.",
                        transfer.getBlockchainTxid(),
                        transfer.getId());
                return null;
            }
            throw exception;
        }
    }

    private boolean isPrunedTransactionLookupFailure(RuntimeException exception) {
        String message = exceptionChainMessage(exception).toLowerCase(Locale.ROOT);
        return message.contains("getrawtransaction")
                && (message.contains("no such mempool or blockchain transaction")
                || message.contains("use -txindex")
                || message.contains("not in mempool")
                || message.contains("transaction index is disabled")
                || message.contains("code\":-5")
                || message.contains("code: -5")
                || message.contains("failed: no such"));
    }

    private String exceptionChainMessage(Throwable throwable) {
        StringBuilder message = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                message.append(current.getMessage()).append('\n');
            }
            for (Throwable suppressed : current.getSuppressed()) {
                if (suppressed.getMessage() != null) {
                    message.append(suppressed.getMessage()).append('\n');
                }
            }
            current = current.getCause();
        }
        return message.toString();
    }

    private int inspectStaleProviderPending(FinancialReconciliationRunEntity run, ExternalTransferEntity transfer) {
        ExternalProviderOutboxEntity outbox = outboxService.findLatestByTransferId(transfer.getId()).orElse(null);
        if (outbox == null) {
            markProviderPendingManual(
                    run,
                    transfer,
                    "Provider pending transfer has no durable outbox row to retry.");
            return 1;
        }

        if (isRetryableOutbox(outbox)) {
            eventService.warn(
                    transfer,
                    "RECONCILIATION_PROVIDER_RETRY_SCHEDULED",
                    outbox.getIdempotencyKey(),
                    "Provider outbox worker will retry status=" + outbox.getStatus()
                            + " attempts=" + outbox.getAttempts());
            recordIssue(
                    run,
                    transfer.getId(),
                    "PROVIDER_SAGA_RETRY_SCHEDULED",
                    "HIGH",
                    outbox.getIdempotencyKey(),
                    "Provider outbox item is due for retry by ExternalProviderOutboxWorker.",
                    "PENDING_AUTO_RETRY",
                    null);
            return 1;
        }

        if ("FAILED_FINAL".equalsIgnoreCase(outbox.getStatus()) && !hasExternalReference(transfer, outbox)) {
            transfer.setStatus("PROVIDER_FAILED");
            externalTransferRepository.save(transfer);
            return inspectProviderFailure(run, transfer);
        }

        markProviderPendingManual(
                run,
                transfer,
                "Provider pending transfer has ambiguous outbox status=" + outbox.getStatus() + ".");
        return 1;
    }

    private void markProviderPendingManual(
            FinancialReconciliationRunEntity run,
            ExternalTransferEntity transfer,
            String details) {
        transfer.setStatus("AUTO_RESOLUTION_PENDING");
        externalTransferRepository.save(transfer);
        eventService.warn(
                transfer,
                "RECONCILIATION_PROVIDER_AUTO_RESOLUTION_PENDING",
                transfer.getExternalReference(),
                "Transfer is waiting for manual reconciliation after provider saga did not complete.");
        recordIssue(
                run,
                transfer.getId(),
                "PROVIDER_SAGA_INCOMPLETE",
                "HIGH",
                transfer.getExternalReference(),
                details,
                "PENDING_MANUAL",
                null);
    }

    private int inspectConfirmationRegression(
            FinancialReconciliationRunEntity run,
            ExternalTransferEntity transfer,
            int observedConfirmations) {
        int storedConfirmations = transfer.getConfirmations() != null ? transfer.getConfirmations() : 0;
        eventService.warn(
                transfer,
                "RECONCILIATION_CONFIRMATION_REGRESSION",
                transfer.getBlockchainTxid(),
                "observedConfirmations=" + observedConfirmations
                        + " storedConfirmations=" + storedConfirmations);

        if (isCreditedInbound(transfer)) {
            BigDecimal reversalAmount = creditedInboundAmount(transfer);
            if (reversalAmount.signum() <= 0) {
                markRegressionManual(
                        run,
                        transfer,
                        observedConfirmations,
                        storedConfirmations,
                        "Inbound transfer appears credited, but net credit cannot be derived for automatic reversal.");
                return 1;
            }

            try {
                ledgerPort.ensureBalance(transfer.getWalletId(), reversalAmount);
            } catch (LedgerExceptions.InsufficientBalanceException insufficientBalance) {
                markRegressionManual(
                        run,
                        transfer,
                        observedConfirmations,
                        storedConfirmations,
                        "Wallet balance is insufficient for automatic confirmation-regression reversal.");
                return 1;
            }

            String reversalKey = "confirmation-regression-reversal:" + transfer.getId() + ":" + transfer.getBlockchainTxid();
            boolean reversed = processedTransactionService.processOnce(
                    reversalKey,
                    "CONFIRMATION_REGRESSION_REVERSAL",
                    () -> ledgerPort.updateBalance(
                            transfer.getWalletId(),
                            reversalAmount.negate(),
                            "CONFIRMATION_REGRESSION_REVERSAL:" + transfer.getId()));
            transfer.setStatus("FAILED_SAFE");
            transfer.setConfirmations(Math.max(0, observedConfirmations));
            transfer.setProviderPayload(appendResolutionReason(
                    transfer.getProviderPayload(),
                    reversed
                            ? "confirmation regression auto-reversed net credit=" + reversalAmount.toPlainString()
                            : "confirmation regression reversal already processed"));
            externalTransferRepository.save(transfer);
            eventService.info(
                    transfer,
                    reversed
                            ? "RECONCILIATION_CONFIRMATION_REGRESSION_REVERSED"
                            : "RECONCILIATION_CONFIRMATION_REGRESSION_ALREADY_REVERSED",
                    transfer.getBlockchainTxid(),
                    "amountBtc=" + reversalAmount.toPlainString()
                            + " | observedConfirmations=" + observedConfirmations
                            + " | storedConfirmations=" + storedConfirmations);
            recordIssue(
                    run,
                    transfer.getId(),
                    "CONFIRMATION_REGRESSION_AUTO_REVERSED",
                    "CRITICAL",
                    transfer.getBlockchainTxid(),
                    "Credited inbound transfer regressed; net credit was reversed idempotently.",
                    "AUTO_RESOLVED",
                    "Reversal key " + reversalKey);
            return 1;
        }

        markRegressionManual(
                run,
                transfer,
                observedConfirmations,
                storedConfirmations,
                "Confirmation regression was detected before a safe credited inbound reversal path was available.");
        return 1;
    }

    private void markRegressionManual(
            FinancialReconciliationRunEntity run,
            ExternalTransferEntity transfer,
            int observedConfirmations,
            int storedConfirmations,
            String details) {
        transfer.setStatus("AUTO_RESOLUTION_PENDING");
        transfer.setConfirmations(Math.max(0, observedConfirmations));
        transfer.setProviderPayload(appendResolutionReason(
                transfer.getProviderPayload(),
                "confirmation_regression observed=" + observedConfirmations
                        + " previous=" + storedConfirmations));
        externalTransferRepository.save(transfer);
        recordIssue(
                run,
                transfer.getId(),
                "CONFIRMATION_REGRESSION",
                "CRITICAL",
                transfer.getBlockchainTxid(),
                details + " Stored confirmations regressed from " + storedConfirmations
                        + " to " + observedConfirmations + ".",
                "PENDING_MANUAL",
                null);
    }

    private int inspectProviderFailure(FinancialReconciliationRunEntity run, ExternalTransferEntity transfer) {
        ExternalProviderOutboxEntity outbox = outboxService.findLatestByTransferId(transfer.getId()).orElse(null);
        if (hasExternalReference(transfer, outbox)) {
            transfer.setStatus("AUTO_RESOLUTION_PENDING");
            externalTransferRepository.save(transfer);
            eventService.warn(
                    transfer,
                    "RECONCILIATION_PROVIDER_FAILURE_AMBIGUOUS",
                    providerReference(transfer, outbox),
                    "Provider failure has an external reference and requires manual resolution.");
            recordIssue(
                    run,
                    transfer.getId(),
                    "PROVIDER_FAILURE_AMBIGUOUS",
                    "CRITICAL",
                    providerReference(transfer, outbox),
                    "Provider failure has an external reference; automatic refund is blocked.",
                    "PENDING_MANUAL",
                    null);
            return 1;
        }

        if (transfer.getWalletId() == null
                || transfer.getTotalDebitedBtc() == null
                || transfer.getTotalDebitedBtc().compareTo(BigDecimal.ZERO) <= 0) {
            transfer.setStatus("AUTO_RESOLUTION_PENDING");
            externalTransferRepository.save(transfer);
            eventService.warn(
                    transfer,
                    "RECONCILIATION_PROVIDER_FAILURE_REFUND_BLOCKED",
                    transfer.getExternalReference(),
                    "Transfer lacks walletId or totalDebitedBtc for automatic refund.");
            recordIssue(
                    run,
                    transfer.getId(),
                    "PROVIDER_FAILURE_REFUND_BLOCKED",
                    "HIGH",
                    transfer.getExternalReference(),
                    "Provider failure cannot be auto-refunded because required accounting fields are missing.",
                    "PENDING_MANUAL",
                    null);
            return 1;
        }

        String refundKey = "external-provider-final-refund:" + transfer.getId();
        boolean refunded = processedTransactionService.processOnce(
                refundKey,
                "EXTERNAL_PROVIDER_FINAL_REFUND",
                () -> ledgerPort.updateBalance(
                        transfer.getWalletId(),
                        transfer.getTotalDebitedBtc(),
                        "EXTERNAL_PROVIDER_FINAL_REFUND:" + transfer.getId()));
        transfer.setStatus("FAILED_SAFE");
        transfer.setProviderPayload(appendResolutionReason(
                transfer.getProviderPayload(),
                refunded ? "provider final failure auto-refunded" : "provider final failure refund already processed"));
        externalTransferRepository.save(transfer);
        eventService.info(
                transfer,
                refunded ? "RECONCILIATION_PROVIDER_AUTO_REFUNDED" : "RECONCILIATION_PROVIDER_REFUND_ALREADY_APPLIED",
                transfer.getId().toString(),
                "amountBtc=" + transfer.getTotalDebitedBtc());
        recordIssue(
                run,
                transfer.getId(),
                "PROVIDER_FAILURE_AUTO_REFUNDED",
                "INFO",
                transfer.getId().toString(),
                "Provider failed before exposing an external reference; locked debit was returned idempotently.",
                "AUTO_RESOLVED",
                "Refund key " + refundKey);
        return 1;
    }

    private boolean staleProviderPending(ExternalTransferEntity transfer) {
        return "PROVIDER_PENDING".equalsIgnoreCase(transfer.getStatus())
                && transfer.getUpdatedAt() != null
                && transfer.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(15));
    }

    private boolean isRetryableOutbox(ExternalProviderOutboxEntity outbox) {
        if (outbox == null) {
            return false;
        }
        boolean retryableStatus = "PENDING".equalsIgnoreCase(outbox.getStatus())
                || "FAILED_RETRYABLE".equalsIgnoreCase(outbox.getStatus())
                || "PROCESSING".equalsIgnoreCase(outbox.getStatus());
        return retryableStatus
                && outbox.getNextAttemptAt() != null
                && !outbox.getNextAttemptAt().isAfter(LocalDateTime.now());
    }

    private boolean isCreditedInbound(ExternalTransferEntity transfer) {
        return "COMPLETED".equalsIgnoreCase(transfer.getStatus())
                && !"OUTBOUND_PAYMENT".equalsIgnoreCase(transfer.getTransferType())
                && transfer.getWalletId() != null;
    }

    private BigDecimal creditedInboundAmount(ExternalTransferEntity transfer) {
        BigDecimal gross = transfer.getAmountBtc() != null ? transfer.getAmountBtc() : BigDecimal.ZERO;
        BigDecimal fee = transfer.getPlatformFeeBtc() != null ? transfer.getPlatformFeeBtc() : BigDecimal.ZERO;
        BigDecimal net = gross.subtract(fee);
        return net.signum() > 0 ? net : BigDecimal.ZERO;
    }

    private int confirmations(JsonNode tx) {
        if (tx == null || tx.isNull() || tx.isMissingNode()) {
            return 0;
        }
        JsonNode confirmations = tx.path("confirmations");
        if (confirmations.isNumber()) {
            return Math.max(0, confirmations.asInt());
        }
        JsonNode status = tx.path("status");
        return status.path("confirmed").asBoolean(false) ? 1 : 0;
    }

    private FinancialReconciliationIssueEntity recordIssue(
            FinancialReconciliationRunEntity run,
            java.util.UUID transferId,
            String issueType,
            String severity,
            String reference,
            String details) {
        return recordIssue(run, transferId, issueType, severity, reference, details, "PENDING", null);
    }

    private FinancialReconciliationIssueEntity recordIssue(
            FinancialReconciliationRunEntity run,
            java.util.UUID transferId,
            String issueType,
            String severity,
            String reference,
            String details,
            String resolutionStatus,
            String resolutionNote) {
        FinancialReconciliationIssueEntity issue = new FinancialReconciliationIssueEntity();
        issue.setRunId(run.getId());
        issue.setTransferId(transferId);
        issue.setIssueType(issueType);
        issue.setSeverity(severity);
        issue.setReference(reference);
        issue.setDetails(details);
        issue.setResolutionStatus(resolutionStatus);
        issue.setResolutionNote(resolutionNote);
        if ("AUTO_RESOLVED".equals(resolutionStatus)) {
            issue.setStatus("RESOLVED");
            issue.setResolvedAt(LocalDateTime.now());
            issue.setResolvedBy("financial-reconciliation");
        }
        issueRepository.save(issue);
        auditTrailService.recordBestEffort(
                "FINANCIAL_RECONCILIATION_ISSUE",
                "RECONCILIATION_ISSUE",
                issue.getId().toString(),
                null,
                reference,
                Map.of(
                        "issueType", issueType,
                        "severity", severity,
                        "transferId", transferId != null ? transferId.toString() : "",
                        "resolutionStatus", resolutionStatus != null ? resolutionStatus : ""));
        metrics.increment("reconciliation_issue", severity, issueType);
        return issue;
    }

    private String appendResolutionReason(String existing, String reason) {
        String base = existing != null ? existing : "";
        String appended = base + "\nautoResolution=" + reason;
        return appended.length() > 4000 ? appended.substring(0, 4000) : appended;
    }

    private boolean hasExternalReference(ExternalTransferEntity transfer, ExternalProviderOutboxEntity outbox) {
        return providerReference(transfer, outbox) != null;
    }

    private String providerReference(ExternalTransferEntity transfer, ExternalProviderOutboxEntity outbox) {
        if (transfer.getExternalReference() != null && !transfer.getExternalReference().isBlank()) {
            return transfer.getExternalReference();
        }
        if (transfer.getBlockchainTxid() != null && !transfer.getBlockchainTxid().isBlank()) {
            return transfer.getBlockchainTxid();
        }
        if (transfer.getPaymentHash() != null && !transfer.getPaymentHash().isBlank()) {
            return transfer.getPaymentHash();
        }
        return outbox != null && outbox.getProviderReference() != null && !outbox.getProviderReference().isBlank()
                ? outbox.getProviderReference()
                : null;
    }
}
