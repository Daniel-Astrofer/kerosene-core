package source.treasury.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.infra.logging.LogSanitizer;
import source.ledger.entity.SiphonRequest;
import source.ledger.entity.SiphonRequestStatus;
import source.ledger.repository.LedgerEntryRepository;
import source.ledger.repository.SiphonRequestRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class TreasuryPayoutExecutionProcessor {

    private final SiphonRequestRepository repository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TreasuryPayoutRailExecutor railExecutor;
    private final FinancialAuditTrailService auditTrailService;
    private final TreasuryPayoutService payoutService;
    private final int maxAttempts;

    public TreasuryPayoutExecutionProcessor(
            SiphonRequestRepository repository,
            LedgerEntryRepository ledgerEntryRepository,
            TreasuryPayoutRailExecutor railExecutor,
            FinancialAuditTrailService auditTrailService,
            TreasuryPayoutService payoutService,
            @Value("${treasury.payout.max-attempts:5}") int maxAttempts) {
        this.repository = repository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.railExecutor = railExecutor;
        this.auditTrailService = auditTrailService;
        this.payoutService = payoutService;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Transactional
    public void process(UUID requestId) {
        SiphonRequest request = repository.findByIdForUpdate(requestId).orElse(null);
        if (request == null || !isClaimedForExecution(request)) {
            return;
        }

        BigDecimal availableAtCutoff = payoutService.normalizeBtc(
                ledgerEntryRepository.calculatePlatformProfitPendingUpTo(request.getRevenueCutoffAt()));
        if (availableAtCutoff.compareTo(request.getAmount()) != 0) {
            markFailed(
                    request,
                    "TREASURY_PAYOUT_REVENUE_SNAPSHOT_MISMATCH",
                    "Pending fee snapshot changed before payout execution.",
                    false);
            return;
        }

        try {
            TreasuryPayoutRailExecutor.ExecutionResult result = railExecutor.execute(request);
            markSettled(request, result);
        } catch (RuntimeException exception) {
            boolean retryable = request.getAttempts() + 1 < maxAttempts;
            markFailed(
                    request,
                    retryable ? "TREASURY_PAYOUT_PROVIDER_RETRYABLE" : "TREASURY_PAYOUT_PROVIDER_FINAL",
                    safeMessage(exception),
                    retryable);
        }
    }

    private void markSettled(
            SiphonRequest request,
            TreasuryPayoutRailExecutor.ExecutionResult result) {
        int collectedRows = ledgerEntryRepository.markFeesAsCollectedUpTo(request.getRevenueCutoffAt());
        LocalDateTime now = LocalDateTime.now();
        request.setStatus(SiphonRequestStatus.SETTLED);
        request.setProviderReference(result.providerReference());
        request.setBlockchainTxid(result.blockchainTxid());
        request.setProviderStatus(result.providerStatus());
        request.setExecutedAt(now);
        request.setRetryable(false);
        request.setLastError(null);
        request.setClaimedBy(null);
        request.setClaimedAt(null);
        request.setUpdatedAt(now);

        auditTrailService.recordBestEffort(
                "TREASURY_PAYOUT_SETTLED",
                "TREASURY_PAYOUT",
                request.getId().toString(),
                null,
                firstNonBlank(result.blockchainTxid(), result.providerReference()),
                Map.of(
                        "amountBtc", request.getAmount().toPlainString(),
                        "destinationRef", LogSanitizer.fingerprint(request.getDestinationAddress()),
                        "providerReference", result.providerReference() != null ? result.providerReference() : "",
                        "providerStatus", result.providerStatus() != null ? result.providerStatus() : "",
                        "networkFeeSats", result.networkFeeSats(),
                        "ledgerRowsCollected", collectedRows));
    }

    private void markFailed(SiphonRequest request, String code, String message, boolean retryable) {
        LocalDateTime now = LocalDateTime.now();
        int nextAttempt = request.getAttempts() + 1;
        request.setAttempts(nextAttempt);
        request.setStatus(SiphonRequestStatus.FAILED);
        request.setRetryable(retryable);
        request.setLastError(code + ": " + message);
        request.setNextAttemptAt(now.plusMinutes(Math.min(60, 1L << Math.min(nextAttempt, 5))));
        request.setClaimedBy(null);
        request.setClaimedAt(null);
        request.setUpdatedAt(now);
        auditTrailService.recordBestEffort(
                retryable ? "TREASURY_PAYOUT_FAILED_RETRYABLE" : "TREASURY_PAYOUT_FAILED_FINAL",
                "TREASURY_PAYOUT",
                request.getId().toString(),
                null,
                LogSanitizer.fingerprint(request.getIdempotencyKey()),
                Map.of(
                        "amountBtc", request.getAmount().toPlainString(),
                        "attempts", request.getAttempts(),
                        "errorCode", code,
                        "retryable", retryable));
    }

    private boolean isClaimedForExecution(SiphonRequest request) {
        return request.getStatus() == SiphonRequestStatus.EXECUTING
                && request.getClaimedBy() != null
                && request.getClaimedAt() != null;
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
