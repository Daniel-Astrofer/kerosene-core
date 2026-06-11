package source.kfe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.kfe.model.KfeBalanceMovementEntity;
import source.kfe.model.KfeDirection;
import source.kfe.model.KfeExecutionOutboxEntity;
import source.kfe.model.KfeRail;
import source.kfe.model.KfeTransactionEntity;
import source.kfe.model.KfeTransactionStatus;
import source.kfe.model.KfeWalletEntity;
import source.kfe.repository.KfeBalanceMovementRepository;
import source.kfe.repository.KfeExecutionOutboxRepository;
import source.kfe.repository.KfeIdempotencyRepository;
import source.kfe.repository.KfeTransactionRepository;
import source.kfe.repository.KfeWalletRepository;
import source.transactions.application.externalpayments.ExternalPaymentsCustodyPort;
import source.transactions.infra.CustodyGateway;
import source.transactions.infra.LightningPaymentGateway;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class KfeExecutionOutboxProcessor {

    private static final String ASSET_BTC = "BTC";

    private final KfeExecutionOutboxRepository outboxRepository;
    private final KfeTransactionRepository transactionRepository;
    private final KfeWalletRepository walletRepository;
    private final KfeIdempotencyRepository idempotencyRepository;
    private final KfeBalanceMovementRepository movementRepository;
    private final KfeBalanceService balanceService;
    private final KfeAuditLogService auditLogService;
    private final KfeStatementService statementService;
    private final KfeDashboardPublisher dashboardPublisher;
    private final KfeHashService hashService;
    private final ExternalPaymentsCustodyPort onchainCustodyPort;
    private final LightningPaymentGateway lightningPaymentGateway;
    private final ObjectMapper objectMapper;

    public KfeExecutionOutboxProcessor(
            KfeExecutionOutboxRepository outboxRepository,
            KfeTransactionRepository transactionRepository,
            KfeWalletRepository walletRepository,
            KfeIdempotencyRepository idempotencyRepository,
            KfeBalanceMovementRepository movementRepository,
            KfeBalanceService balanceService,
            KfeAuditLogService auditLogService,
            KfeStatementService statementService,
            KfeDashboardPublisher dashboardPublisher,
            KfeHashService hashService,
            @Qualifier("bitcoinCorePsbtExternalPaymentsCustodyPort")
            ExternalPaymentsCustodyPort onchainCustodyPort,
            @Qualifier("externalLightningPaymentGateway")
            LightningPaymentGateway lightningPaymentGateway,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.movementRepository = movementRepository;
        this.balanceService = balanceService;
        this.auditLogService = auditLogService;
        this.statementService = statementService;
        this.dashboardPublisher = dashboardPublisher;
        this.hashService = hashService;
        this.onchainCustodyPort = onchainCustodyPort;
        this.lightningPaymentGateway = lightningPaymentGateway;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void process(UUID outboxId) {
        KfeExecutionOutboxEntity outbox = outboxRepository.findByIdForUpdate(outboxId).orElse(null);
        if (outbox == null || !isClaimed(outbox)) {
            return;
        }

        KfeTransactionEntity tx = transactionRepository.findByIdForUpdate(outbox.getTransactionId()).orElse(null);
        if (tx == null) {
            markOutboxFailed(outbox, "TRANSACTION_NOT_FOUND", "KFE transaction does not exist.", false);
            return;
        }
        if (tx.getStatus() == KfeTransactionStatus.SETTLED || tx.getStatus() == KfeTransactionStatus.FAILED) {
            markOutboxDispatched(outbox, firstNonBlank(tx.getProviderReference(), tx.getBlockchainTxid(), tx.getPaymentHash()));
            return;
        }
        if (tx.getStatus() != KfeTransactionStatus.EXECUTING
                && tx.getStatus() != KfeTransactionStatus.REQUIRES_RECONCILIATION) {
            markOutboxFailed(outbox, "INVALID_TRANSACTION_STATUS",
                    "KFE transaction is not executable in status " + tx.getStatus() + ".", false);
            return;
        }

        try {
            switch (operation(outbox)) {
                case "ONCHAIN_OUTBOUND" -> processOnchainOutbound(outbox, tx);
                case "LIGHTNING_OUTBOUND" -> processLightningOutbound(outbox, tx);
                case "ONCHAIN_INBOUND", "LIGHTNING_INBOUND" -> markRequiresReconciliation(
                        outbox,
                        tx,
                        "INBOUND_REQUIRES_TRUSTED_MONITOR",
                        "Inbound settlement must be performed by a trusted KFE network monitor.");
                default -> markFinalFailure(outbox, tx, "UNSUPPORTED_OPERATION",
                        "Unsupported KFE outbox operation " + outbox.getOperation() + ".");
            }
        } catch (ExternalPaymentsCustodyPort.ProviderExecutionAmbiguous ambiguous) {
            markUnknown(outbox, tx, ambiguous.providerReference(), ambiguous.rawPayload(), ambiguous.getMessage());
        } catch (RuntimeException exception) {
            boolean retryable = isRetryable(exception);
            if (retryable) {
                markRetryableFailure(outbox, tx, "PROVIDER_RETRYABLE_FAILURE", safeMessage(exception));
            } else {
                markFinalFailure(outbox, tx, "PROVIDER_FINAL_FAILURE", safeMessage(exception));
            }
        }
    }

    private void processOnchainOutbound(KfeExecutionOutboxEntity outbox, KfeTransactionEntity tx) {
        KfeWalletEntity sourceWallet = sourceWallet(tx);
        JsonNode payload = payload(outbox);
        String destination = text(payload, "externalReference", null);
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("externalReference must contain the destination address.");
        }

        ExternalPaymentsCustodyPort.PaymentResult result = onchainCustodyPort.sendOnchain(
                new ExternalPaymentsCustodyPort.OnchainPaymentCommand(
                        tx.getUserId(),
                        null,
                        sourceWallet.getLabel(),
                        destination,
                        tx.getReceiverAmountSats(),
                        tx.getNetworkFeeSats(),
                        text(payload, "memo", "KFE on-chain outbound"),
                        tx.getIdempotencyKey(),
                        tx.getQuorumProposalHash()));

        String providerReference = firstNonBlank(result.txid(), result.providerReference());
        tx.setProvider(onchainCustodyPort.providerName());
        tx.setProviderReference(providerReference);
        tx.setBlockchainTxid(result.txid());
        if (result.feeSats() > 0L) {
            tx.setNetworkFeeSats(result.feeSats());
        }
        settleOutbound(outbox, tx, sourceWallet.getId(), providerReference, result.rawPayload());
    }

    private void processLightningOutbound(KfeExecutionOutboxEntity outbox, KfeTransactionEntity tx) {
        KfeWalletEntity sourceWallet = sourceWallet(tx);
        JsonNode payload = payload(outbox);
        String paymentRequest = text(payload, "externalReference", null);
        if (paymentRequest == null || paymentRequest.isBlank()) {
            throw new IllegalArgumentException("externalReference must contain the Lightning payment request.");
        }

        CustodyGateway.PaymentResult result = lightningPaymentGateway.payLightning(
                new CustodyGateway.LightningPaymentCommand(
                        tx.getUserId(),
                        null,
                        sourceWallet.getLabel(),
                        paymentRequest,
                        tx.getReceiverAmountSats(),
                        tx.getNetworkFeeSats(),
                        text(payload, "memo", "KFE lightning outbound"),
                        tx.getIdempotencyKey(),
                        tx.getQuorumProposalHash()));

        String providerReference = firstNonBlank(result.paymentHash(), result.providerReference(), result.txid());
        tx.setProvider(lightningPaymentGateway.providerName());
        tx.setProviderReference(result.providerReference());
        tx.setBlockchainTxid(result.txid());
        tx.setPaymentHash(providerReference);
        if (result.feeSats() > 0L) {
            tx.setNetworkFeeSats(result.feeSats());
        }
        settleOutbound(outbox, tx, sourceWallet.getId(), providerReference, result.rawPayload());
    }

    private void settleOutbound(
            KfeExecutionOutboxEntity outbox,
            KfeTransactionEntity tx,
            UUID sourceWalletId,
            String providerReference,
            String providerPayload) {
        balanceService.settleReservedDebit(sourceWalletId, ASSET_BTC, tx.getTotalDebitSats());
        movement(tx.getId(), sourceWalletId, "SETTLE_DEBIT", tx.getTotalDebitSats(), "LOCKED", null);
        transition(tx, KfeTransactionStatus.SETTLED, "KFE_TRANSACTION_SETTLED",
                Map.of("providerReferenceHash", hashService.sha256(firstNonBlank(providerReference, ""))));
        recordStatement(tx, sourceWalletId, providerPayload);
        updateIdempotency(tx);
        markOutboxDispatched(outbox, providerReference);
        dashboardPublisher.publishAfterCommit(tx.getUserId());
    }

    private void markUnknown(
            KfeExecutionOutboxEntity outbox,
            KfeTransactionEntity tx,
            String providerReference,
            String providerPayload,
            String message) {
        tx.setProviderReference(firstNonBlank(providerReference, tx.getProviderReference()));
        tx.setFailureCode("PROVIDER_RESULT_UNKNOWN");
        tx.setFailureMessage(trim(safeMessage(message), 255));
        transition(tx, KfeTransactionStatus.REQUIRES_RECONCILIATION, "KFE_TRANSACTION_REQUIRES_RECONCILIATION",
                Map.of("providerReferenceHash", hashService.sha256(firstNonBlank(providerReference, ""))));
        recordStatement(tx, tx.getSourceWalletId(), providerPayload);
        updateIdempotency(tx);

        outbox.setAttempts(outbox.getAttempts() + 1);
        outbox.setStatus("UNKNOWN");
        outbox.setProviderReference(providerReference);
        outbox.setLastError(trim(safeMessage(message), 1000));
        outbox.setNextAttemptAt(null);
        clearClaim(outbox);
        outboxRepository.save(outbox);
        dashboardPublisher.publishAfterCommit(tx.getUserId());
    }

    private void markRetryableFailure(
            KfeExecutionOutboxEntity outbox,
            KfeTransactionEntity tx,
            String code,
            String message) {
        outbox.setAttempts(outbox.getAttempts() + 1);
        outbox.setStatus("FAILED_RETRYABLE");
        outbox.setLastError(trim(code + ": " + message, 1000));
        outbox.setNextAttemptAt(LocalDateTime.now().plusMinutes(Math.min(60, 1L << Math.min(outbox.getAttempts(), 5))));
        clearClaim(outbox);
        outboxRepository.save(outbox);
        audit(tx, "KFE_EXECUTION_RETRYABLE_FAILURE", tx.getStatus(), tx.getStatus(),
                Map.of("failureCode", code, "errorHash", hashService.sha256(message)));
    }

    private void markFinalFailure(
            KfeExecutionOutboxEntity outbox,
            KfeTransactionEntity tx,
            String code,
            String message) {
        if (tx.getSourceWalletId() != null && tx.getTotalDebitSats() > 0L) {
            balanceService.releaseReserved(tx.getSourceWalletId(), ASSET_BTC, tx.getTotalDebitSats());
            movement(tx.getId(), tx.getSourceWalletId(), "RELEASE_RESERVE", tx.getTotalDebitSats(), "LOCKED", "AVAILABLE");
        }
        tx.setFailureCode(trim(code, 64));
        tx.setFailureMessage(trim(message, 255));
        transition(tx, KfeTransactionStatus.FAILED, "KFE_TRANSACTION_FAILED",
                Map.of("failureCode", code, "errorHash", hashService.sha256(message)));
        recordStatement(tx, firstNonNull(tx.getSourceWalletId(), tx.getDestinationWalletId()), null);
        updateIdempotency(tx);
        markOutboxFailed(outbox, code, message, false);
        dashboardPublisher.publishAfterCommit(tx.getUserId());
    }

    private void markRequiresReconciliation(
            KfeExecutionOutboxEntity outbox,
            KfeTransactionEntity tx,
            String code,
            String message) {
        tx.setFailureCode(trim(code, 64));
        tx.setFailureMessage(trim(message, 255));
        transition(tx, KfeTransactionStatus.REQUIRES_RECONCILIATION, "KFE_TRANSACTION_REQUIRES_RECONCILIATION",
                Map.of("reason", code));
        recordStatement(tx, firstNonNull(tx.getDestinationWalletId(), tx.getSourceWalletId()), null);
        updateIdempotency(tx);

        outbox.setAttempts(outbox.getAttempts() + 1);
        outbox.setStatus("UNKNOWN");
        outbox.setLastError(trim(code + ": " + message, 1000));
        outbox.setNextAttemptAt(null);
        clearClaim(outbox);
        outboxRepository.save(outbox);
        dashboardPublisher.publishAfterCommit(tx.getUserId());
    }

    private void markOutboxDispatched(KfeExecutionOutboxEntity outbox, String providerReference) {
        outbox.setStatus("DISPATCHED");
        outbox.setProviderReference(providerReference);
        outbox.setDispatchedAt(LocalDateTime.now());
        outbox.setLastError(null);
        outbox.setNextAttemptAt(null);
        clearClaim(outbox);
        outboxRepository.save(outbox);
    }

    private void markOutboxFailed(
            KfeExecutionOutboxEntity outbox,
            String code,
            String message,
            boolean retryable) {
        outbox.setAttempts(outbox.getAttempts() + 1);
        outbox.setStatus(retryable ? "FAILED_RETRYABLE" : "FAILED_FINAL");
        outbox.setLastError(trim(code + ": " + safeMessage(message), 1000));
        outbox.setNextAttemptAt(retryable
                ? LocalDateTime.now().plusMinutes(Math.min(60, 1L << Math.min(outbox.getAttempts(), 5)))
                : null);
        clearClaim(outbox);
        outboxRepository.save(outbox);
    }

    private void transition(
            KfeTransactionEntity tx,
            KfeTransactionStatus target,
            String eventType,
            Map<String, ?> auditPayload) {
        KfeTransactionStatus previous = tx.getStatus();
        tx.setStatus(target);
        transactionRepository.save(tx);
        audit(tx, eventType, previous, target, auditPayload);
    }

    private void audit(
            KfeTransactionEntity tx,
            String eventType,
            KfeTransactionStatus from,
            KfeTransactionStatus to,
            Map<String, ?> payload) {
        Map<String, Object> redacted = new LinkedHashMap<>();
        redacted.put("transactionId", tx.getId().toString());
        redacted.put("idempotencyHash", hashService.sha256(tx.getIdempotencyKey()));
        if (payload != null) {
            redacted.putAll(payload);
        }
        auditLogService.record(eventType, tx.getId(), tx.getSourceWalletId(), from, to, redacted);
    }

    private void movement(
            UUID transactionId,
            UUID walletId,
            String movementType,
            long amountSats,
            String fromBucket,
            String toBucket) {
        KfeBalanceMovementEntity movement = new KfeBalanceMovementEntity();
        movement.setTransactionId(transactionId);
        movement.setWalletId(walletId);
        movement.setMovementType(movementType);
        movement.setAmountSats(amountSats);
        movement.setFromBucket(fromBucket);
        movement.setToBucket(toBucket);
        movementRepository.save(movement);
    }

    private void recordStatement(KfeTransactionEntity tx, UUID walletId, String providerPayload) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", tx.getId().toString());
        payload.put("status", tx.getStatus().name());
        payload.put("rail", tx.getRail().name());
        payload.put("direction", tx.getDirection().name());
        payload.put("grossAmountSats", tx.getGrossAmountSats());
        payload.put("receiverAmountSats", tx.getReceiverAmountSats());
        payload.put("networkFeeSats", tx.getNetworkFeeSats());
        payload.put("keroseneFeeSats", tx.getKeroseneFeeSats());
        payload.put("totalDebitSats", tx.getTotalDebitSats());
        payload.put("provider", tx.getProvider());
        payload.put("providerReferenceHash", hashService.sha256(firstNonBlank(tx.getProviderReference(), "")));
        payload.put("blockchainTxid", tx.getBlockchainTxid());
        payload.put("paymentHash", tx.getPaymentHash());
        if (providerPayload != null && !providerPayload.isBlank()) {
            payload.put("providerPayloadHash", hashService.sha256(providerPayload));
        }
        statementService.recordUserStatement(tx.getUserId(), walletId, tx, payload);
    }

    private void updateIdempotency(KfeTransactionEntity tx) {
        idempotencyRepository.findById(tx.getIdempotencyKey()).ifPresent(entity -> {
            entity.setStatus(tx.getStatus().name());
            idempotencyRepository.save(entity);
        });
    }

    private KfeWalletEntity sourceWallet(KfeTransactionEntity tx) {
        if (tx.getSourceWalletId() == null) {
            throw new IllegalStateException("Source wallet is required for outbound execution.");
        }
        return walletRepository.findById(tx.getSourceWalletId())
                .orElseThrow(() -> new IllegalStateException("Source KFE wallet not found."));
    }

    private JsonNode payload(KfeExecutionOutboxEntity outbox) {
        if (outbox.getPayloadJson() == null || outbox.getPayloadJson().isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(outbox.getPayloadJson());
        } catch (Exception exception) {
            throw new IllegalArgumentException("KFE outbox payload is not valid JSON.", exception);
        }
    }

    private String operation(KfeExecutionOutboxEntity outbox) {
        return outbox.getOperation() != null ? outbox.getOperation().trim().toUpperCase() : "";
    }

    private boolean isClaimed(KfeExecutionOutboxEntity outbox) {
        return "PROCESSING".equals(outbox.getStatus())
                && outbox.getClaimedBy() != null
                && outbox.getClaimedAt() != null;
    }

    private boolean isRetryable(RuntimeException exception) {
        return !(exception instanceof IllegalArgumentException)
                && !(exception instanceof UnsupportedOperationException)
                && !(exception instanceof IllegalStateException);
    }

    private void clearClaim(KfeExecutionOutboxEntity outbox) {
        outbox.setClaimedBy(null);
        outbox.setClaimedAt(null);
    }

    private String text(JsonNode payload, String field, String fallback) {
        JsonNode value = payload.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : fallback;
    }

    private String safeMessage(Throwable exception) {
        return exception.getMessage() != null && !exception.getMessage().isBlank()
                ? exception.getMessage()
                : "KFE provider execution failed.";
    }

    private String safeMessage(String message) {
        return message != null && !message.isBlank() ? message : "KFE provider execution failed.";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private UUID firstNonNull(UUID first, UUID second) {
        return first != null ? first : second;
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
