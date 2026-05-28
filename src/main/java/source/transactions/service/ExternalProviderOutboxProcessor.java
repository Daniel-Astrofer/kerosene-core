package source.transactions.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.observability.FinancialOperationsMetrics;
import source.transactions.application.externalpayments.ExternalPaymentsCustodyPort;
import source.transactions.application.externalpayments.ExternalPaymentsMath;
import source.transactions.infra.CustodyGateway;
import source.transactions.infra.LightningPaymentGateway;
import source.transactions.model.ExternalProviderOutboxEntity;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.repository.ExternalProviderOutboxRepository;
import source.transactions.repository.ExternalTransferRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class ExternalProviderOutboxProcessor {

    private final ExternalProviderOutboxRepository outboxRepository;
    private final ExternalTransferRepository transferRepository;
    private final ExternalPaymentsCustodyPort onchainCustodyPort;
    private final LightningPaymentGateway lightningPaymentGateway;
    private final ExternalPaymentsMath externalPaymentsMath;
    private final NetworkTransferEventService eventService;
    private final FinancialOperationsMetrics metrics;
    private final ObjectMapper objectMapper;

    public ExternalProviderOutboxProcessor(
            ExternalProviderOutboxRepository outboxRepository,
            ExternalTransferRepository transferRepository,
            @Qualifier("bitcoinCorePsbtExternalPaymentsCustodyPort")
            ExternalPaymentsCustodyPort onchainCustodyPort,
            @Qualifier("externalLightningPaymentGateway")
            LightningPaymentGateway lightningPaymentGateway,
            ExternalPaymentsMath externalPaymentsMath,
            NetworkTransferEventService eventService,
            FinancialOperationsMetrics metrics,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.transferRepository = transferRepository;
        this.onchainCustodyPort = onchainCustodyPort;
        this.lightningPaymentGateway = lightningPaymentGateway;
        this.externalPaymentsMath = externalPaymentsMath;
        this.eventService = eventService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void process(UUID outboxId) {
        ExternalProviderOutboxEntity outbox = outboxRepository.findByIdForUpdate(outboxId).orElse(null);
        if (outbox == null || !isClaimedForProcessing(outbox)) {
            return;
        }

        ExternalTransferEntity transfer = transferRepository.findById(outbox.getTransferId()).orElse(null);
        if (transfer == null) {
            markFailed(outbox, null, "TRANSFER_NOT_FOUND", "External transfer does not exist.", false);
            return;
        }

        String existingReference = existingReference(transfer, outbox);
        if (existingReference != null) {
            markDispatched(outbox, transfer, existingReference, "PROVIDER_OUTBOX_ALREADY_DISPATCHED");
            return;
        }

        try {
            JsonNode payload = payload(outbox);
            switch (operationType(outbox)) {
                case "ONCHAIN_SEND" -> processOnchainSend(outbox, transfer, payload);
                case "LIGHTNING_PAY" -> processLightningPay(outbox, transfer, payload);
                default -> markFailed(
                        outbox,
                        transfer,
                        "UNSUPPORTED_OPERATION_TYPE",
                        "Unsupported provider outbox operationType=" + outbox.getOperationType(),
                        false);
            }
        } catch (ExternalPaymentsCustodyPort.ProviderExecutionAmbiguous ambiguousResult) {
            markUnknown(outbox, transfer, ambiguousResult);
        } catch (RuntimeException exception) {
            boolean retryable = isRetryable(exception);
            markFailed(
                    outbox,
                    transfer,
                    retryable ? "PROVIDER_RETRYABLE_FAILURE" : "PROVIDER_FINAL_FAILURE",
                    exception.getMessage(),
                    retryable);
        }
    }

    private void processOnchainSend(
            ExternalProviderOutboxEntity outbox,
            ExternalTransferEntity transfer,
            JsonNode payload) {
        long amountSats = amountSats(payload, transfer.getAmountBtc());
        String destination = text(payload, "destination", transfer.getDestination());
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Destination address is required for ONCHAIN_SEND.");
        }
        ExternalPaymentsCustodyPort.PaymentResult result = onchainCustodyPort.sendOnchain(
                new ExternalPaymentsCustodyPort.OnchainPaymentCommand(
                        transfer.getUserId(),
                        transfer.getWalletId(),
                        transfer.getWalletNameSnapshot(),
                        destination,
                        amountSats,
                        longValue(payload, "maxFeeSats", btcToSats(transfer.getNetworkFeeBtc())),
                        transfer.getContext(),
                        outbox.getIdempotencyKey(),
                        text(payload, "authorizationProof", "")));

        String externalReference = externalPaymentsMath.firstNonBlank(result.txid(), result.providerReference());
        transfer.setStatus(externalPaymentsMath.firstNonBlank(result.status(), "MEMPOOL"));
        transfer.setExternalReference(externalReference);
        transfer.setBlockchainTxid(result.txid());
        if (result.feeSats() > 0) {
            transfer.setNetworkFeeBtc(externalPaymentsMath.satsToBtc(result.feeSats()));
        }
        transfer.setDetectedAt(transfer.getDetectedAt() != null ? transfer.getDetectedAt() : LocalDateTime.now());
        transfer.setProviderPayload(result.rawPayload());
        transferRepository.save(transfer);
        markDispatched(outbox, transfer, externalReference, "PROVIDER_OUTBOX_DISPATCHED");
    }

    private void processLightningPay(
            ExternalProviderOutboxEntity outbox,
            ExternalTransferEntity transfer,
            JsonNode payload) {
        long amountSats = amountSats(payload, transfer.getAmountBtc());
        String paymentRequest = externalPaymentsMath.firstNonBlank(
                text(payload, "paymentRequest", null),
                transfer.getDestination(),
                transfer.getInvoiceData());
        if (paymentRequest == null || paymentRequest.isBlank()) {
            throw new IllegalArgumentException("Payment request is required for LIGHTNING_PAY.");
        }

        CustodyGateway.PaymentResult result = lightningPaymentGateway.payLightning(
                new CustodyGateway.LightningPaymentCommand(
                        transfer.getUserId(),
                        transfer.getWalletId(),
                        transfer.getWalletNameSnapshot(),
                        paymentRequest,
                        amountSats,
                        longValue(payload, "maxFeeSats", btcToSats(transfer.getNetworkFeeBtc())),
                        transfer.getContext(),
                        outbox.getIdempotencyKey(),
                        text(payload, "authorizationProof", "")));

        String externalReference = externalPaymentsMath.firstNonBlank(result.paymentHash(), result.providerReference());
        transfer.setStatus(externalPaymentsMath.firstNonBlank(result.status(), "SETTLED"));
        transfer.setExternalReference(result.providerReference());
        transfer.setBlockchainTxid(result.txid());
        transfer.setPaymentHash(externalReference);
        if (result.feeSats() > 0) {
            transfer.setNetworkFeeBtc(externalPaymentsMath.satsToBtc(result.feeSats()));
        }
        transfer.setDetectedAt(transfer.getDetectedAt() != null ? transfer.getDetectedAt() : LocalDateTime.now());
        transfer.setProviderPayload(result.rawPayload());
        if (isSettled(transfer.getStatus())) {
            transfer.setSettledAt(LocalDateTime.now());
        }
        transferRepository.save(transfer);
        markDispatched(outbox, transfer, externalReference, "PROVIDER_OUTBOX_DISPATCHED");
    }

    private void markDispatched(
            ExternalProviderOutboxEntity outbox,
            ExternalTransferEntity transfer,
            String providerReference,
            String eventType) {
        outbox.setStatus("DISPATCHED");
        outbox.setProviderReference(providerReference);
        outbox.setDispatchedAt(LocalDateTime.now());
        outbox.setLastError(null);
        outbox.setClaimedBy(null);
        outbox.setClaimedAt(null);
        outboxRepository.save(outbox);
        eventService.info(
                transfer,
                eventType,
                providerReference,
                "operationType=" + outbox.getOperationType() + " | idempotencyKey=" + source.common.infra.logging.LogSanitizer.fingerprint(outbox.getIdempotencyKey()));
        metrics.increment("external_provider_outbox", "dispatched", operationType(outbox));
    }

    private void markUnknown(
            ExternalProviderOutboxEntity outbox,
            ExternalTransferEntity transfer,
            ExternalPaymentsCustodyPort.ProviderExecutionAmbiguous ambiguousResult) {
        outbox.setAttempts(outbox.getAttempts() + 1);
        outbox.setStatus("UNKNOWN");
        outbox.setProviderReference(ambiguousResult.providerReference());
        outbox.setLastError(trim(safeMessage(ambiguousResult.getMessage()), 1000));
        outbox.setNextAttemptAt(LocalDateTime.now());
        outbox.setClaimedBy(null);
        outbox.setClaimedAt(null);
        outboxRepository.save(outbox);

        transfer.setStatus("AUTO_RESOLUTION_PENDING");
        transfer.setProviderPayload(ambiguousResult.rawPayload());
        transferRepository.save(transfer);

        eventService.warn(
                transfer,
                "PROVIDER_OUTBOX_UNKNOWN_RESULT",
                outbox.getIdempotencyKey(),
                "operationType=" + outbox.getOperationType());
        metrics.increment("external_provider_outbox", "unknown", operationType(outbox));
    }

    private void markFailed(
            ExternalProviderOutboxEntity outbox,
            ExternalTransferEntity transfer,
            String errorCode,
            String message,
            boolean retryable) {
        outbox.setAttempts(outbox.getAttempts() + 1);
        outbox.setStatus(retryable ? "FAILED_RETRYABLE" : "FAILED_FINAL");
        outbox.setLastError(trim(errorCode + ": " + safeMessage(message), 1000));
        outbox.setNextAttemptAt(LocalDateTime.now().plusMinutes(Math.min(60, 1L << Math.min(outbox.getAttempts(), 5))));
        outbox.setClaimedBy(null);
        outbox.setClaimedAt(null);
        outboxRepository.save(outbox);

        if (transfer != null && !retryable) {
            transfer.setStatus("PROVIDER_FAILED");
            transfer.setProviderPayload(appendProviderFailure(transfer.getProviderPayload(), errorCode));
            transferRepository.save(transfer);
        }

        if (retryable) {
            eventService.warn(
                    transfer,
                    "PROVIDER_OUTBOX_RETRYABLE_FAILURE",
                    outbox.getIdempotencyKey(),
                    "operationType=" + outbox.getOperationType() + " | errorCode=" + errorCode);
        } else {
            eventService.error(
                    transfer,
                    "PROVIDER_OUTBOX_FINAL_FAILURE",
                    outbox.getIdempotencyKey(),
                    "operationType=" + outbox.getOperationType() + " | errorCode=" + errorCode);
        }
        metrics.increment("external_provider_outbox", retryable ? "retryable_failure" : "final_failure", operationType(outbox));
    }

    private boolean isClaimedForProcessing(ExternalProviderOutboxEntity outbox) {
        return "PROCESSING".equals(outbox.getStatus())
                && outbox.getClaimedBy() != null
                && outbox.getClaimedAt() != null;
    }

    private String existingReference(ExternalTransferEntity transfer, ExternalProviderOutboxEntity outbox) {
        return externalPaymentsMath.firstNonBlank(
                outbox.getProviderReference(),
                transfer.getBlockchainTxid(),
                transfer.getPaymentHash(),
                transfer.getExternalReference());
    }

    private String operationType(ExternalProviderOutboxEntity outbox) {
        return outbox.getOperationType() != null
                ? outbox.getOperationType().trim().toUpperCase(Locale.ROOT)
                : "";
    }

    private JsonNode payload(ExternalProviderOutboxEntity outbox) {
        if (outbox.getPayloadJson() == null || outbox.getPayloadJson().isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(outbox.getPayloadJson());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Provider outbox payload is not valid JSON.", exception);
        }
    }

    private long amountSats(JsonNode payload, BigDecimal fallbackAmountBtc) {
        long value = longValue(payload, "amountSats", btcToSats(fallbackAmountBtc));
        if (value <= 0L) {
            throw new IllegalArgumentException("amountSats must be positive for provider outbox processing.");
        }
        return value;
    }

    private long btcToSats(BigDecimal amountBtc) {
        return amountBtc != null ? externalPaymentsMath.btcToSats(amountBtc.abs()) : 0L;
    }

    private long longValue(JsonNode payload, String field, long fallback) {
        JsonNode value = payload.path(field);
        return value.isNumber() ? value.asLong() : fallback;
    }

    private String text(JsonNode payload, String field, String fallback) {
        JsonNode value = payload.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : fallback;
    }

    private boolean isSettled(String status) {
        return status != null
                && ("SETTLED".equalsIgnoreCase(status)
                || "COMPLETED".equalsIgnoreCase(status)
                || "PAID".equalsIgnoreCase(status));
    }

    private boolean isRetryable(RuntimeException exception) {
        return !(exception instanceof IllegalArgumentException)
                && !(exception instanceof UnsupportedOperationException);
    }

    private String appendProviderFailure(String existing, String errorCode) {
        String value = (existing != null ? existing : "") + "\nproviderOutboxFailure=" + errorCode;
        return trim(value, 4000);
    }

    private String safeMessage(String message) {
        return message != null && !message.isBlank() ? message : "Provider outbox processing failed.";
    }

    private String trim(String value, int maxLength) {
        return value != null && value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
