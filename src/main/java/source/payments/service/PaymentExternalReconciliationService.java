package source.payments.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.ledger.service.LedgerContract;
import source.payments.model.PaymentEnums;
import source.payments.model.PaymentExecutionOutboxEntity;
import source.payments.model.PaymentIntentEntity;
import source.payments.repository.PaymentExecutionOutboxRepository;
import source.payments.repository.PaymentIntentRepository;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")
public class PaymentExternalReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentExternalReconciliationService.class);
    private static final BigDecimal SATS_PER_BTC = new BigDecimal("100000000");
    private static final List<PaymentEnums.PaymentIntentStatus> RECONCILABLE_STATUSES = List.of(
            PaymentEnums.PaymentIntentStatus.ACCEPTED_BY_PROVIDER,
            PaymentEnums.PaymentIntentStatus.REQUIRES_RECONCILIATION);

    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentExecutionOutboxRepository outboxRepository;
    private final WalletRepository walletRepository;
    private final LedgerContract ledgerService;
    private final PaymentAuditService paymentAuditService;
    private final PaymentStateMachine paymentStateMachine;
    private final Map<PaymentEnums.PaymentRail, PaymentRailStatusClient> clientsByRail;

    public PaymentExternalReconciliationService(
            PaymentIntentRepository paymentIntentRepository,
            PaymentExecutionOutboxRepository outboxRepository,
            WalletRepository walletRepository,
            LedgerContract ledgerService,
            PaymentAuditService paymentAuditService,
            PaymentStateMachine paymentStateMachine,
            List<PaymentRailStatusClient> statusClients) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.outboxRepository = outboxRepository;
        this.walletRepository = walletRepository;
        this.ledgerService = ledgerService;
        this.paymentAuditService = paymentAuditService;
        this.paymentStateMachine = paymentStateMachine;
        this.clientsByRail = statusClients.stream().collect(Collectors.toUnmodifiableMap(
                PaymentRailStatusClient::rail,
                Function.identity(),
                (left, right) -> left));
    }

    @Scheduled(
            fixedDelayString = "${payments.reconciliation.fixed-delay-ms:30000}",
            initialDelayString = "${payments.reconciliation.initial-delay-ms:90000}")
    public void reconcileDuePayments() {
        try {
            paymentIntentRepository.findTop50ByStatusInOrderByUpdatedAtAsc(RECONCILABLE_STATUSES)
                    .forEach(intent -> {
                        try {
                            reconcile(intent.getId());
                        } catch (RuntimeException exception) {
                            log.warn("[PaymentReconciliation] intentId={} failed: {}",
                                    intent.getId(),
                                    exception.getMessage());
                        }
                    });
        } catch (DataAccessException exception) {
            log.warn("[PaymentReconciliation] Storage unavailable. Worker will retry later: {}",
                    exception.getMostSpecificCause().getMessage());
        }
    }

    @Transactional
    public void reconcile(UUID paymentIntentId) {
        PaymentIntentEntity intent = paymentIntentRepository.findByIdForUpdate(paymentIntentId).orElse(null);
        if (intent == null || !RECONCILABLE_STATUSES.contains(intent.getStatus())) {
            return;
        }

        PaymentExecutionOutboxEntity outbox = outboxRepository.findByPaymentIntentId(intent.getId()).orElse(null);
        Optional<PaymentRailStatusClient> client = Optional.ofNullable(clientsByRail.get(intent.getRail()));
        if (client.isEmpty()) {
            markProviderStatusUnavailable(intent, outbox);
            return;
        }

        PaymentRailStatusClient.StatusResult result = client.get().status(intent, outbox);
        applyStatusResult(intent, outbox, result);
        paymentAuditService.record(intent.getSenderUserId(), intent.getId(), "PAYMENT_RECONCILED", Map.of(
                "rail", intent.getRail().name(),
                "outcome", result.outcome().name(),
                "providerReference", firstNonBlank(result.providerReference(), providerReference(outbox)),
                "providerStatus", firstNonBlank(result.providerStatus(), "")));
    }

    private void applyStatusResult(
            PaymentIntentEntity intent,
            PaymentExecutionOutboxEntity outbox,
            PaymentRailStatusClient.StatusResult result) {
        switch (result.outcome()) {
            case ACCEPTED -> markAccepted(intent, outbox, result);
            case SETTLED -> markSettled(intent, outbox, result);
            case FAILED_RETRYABLE, UNKNOWN -> markUnknown(intent, outbox, result);
            case FAILED_FINAL -> refundAndFail(intent, outbox, result);
        }
    }

    private void markProviderStatusUnavailable(PaymentIntentEntity intent, PaymentExecutionOutboxEntity outbox) {
        if (intent.getStatus() == PaymentEnums.PaymentIntentStatus.ACCEPTED_BY_PROVIDER) {
            paymentStateMachine.requireReconciliation(
                    intent,
                    "PAYMENT_RECONCILIATION_PROVIDER_NOT_CONFIGURED",
                    "Este envio aguarda reconciliacao porque a consulta do provider nao esta configurada.");
        }
        if (outbox != null) {
            outbox.setStatus("UNKNOWN");
            outbox.setLastError("PAYMENT_RECONCILIATION_PROVIDER_NOT_CONFIGURED");
            outbox.setNextAttemptAt(Instant.now().plusSeconds(300));
            clearClaim(outbox);
        }
        paymentAuditService.record(intent.getSenderUserId(), intent.getId(), "PAYMENT_RECONCILIATION_WAITING", Map.of(
                "rail", intent.getRail().name(),
                "reason", "PAYMENT_RECONCILIATION_PROVIDER_NOT_CONFIGURED"));
    }

    private void markAccepted(
            PaymentIntentEntity intent,
            PaymentExecutionOutboxEntity outbox,
            PaymentRailStatusClient.StatusResult result) {
        if (intent.getStatus() == PaymentEnums.PaymentIntentStatus.PROCESSING) {
            paymentStateMachine.acceptByProvider(intent);
        }
        updateOutboxProviderState(outbox, result, "DISPATCHED", null);
    }

    private void markSettled(
            PaymentIntentEntity intent,
            PaymentExecutionOutboxEntity outbox,
            PaymentRailStatusClient.StatusResult result) {
        paymentStateMachine.settle(intent);
        updateOutboxProviderState(outbox, result, "SETTLED", null);
    }

    private void markUnknown(
            PaymentIntentEntity intent,
            PaymentExecutionOutboxEntity outbox,
            PaymentRailStatusClient.StatusResult result) {
        String failureCode = firstNonBlank(result.failureCode(), "PAYMENT_RECONCILIATION_UNKNOWN");
        String failureMessage = firstNonBlank(
                result.safeFailureMessage(),
                "O envio externo ainda precisa de reconciliacao.");
        if (intent.getStatus() == PaymentEnums.PaymentIntentStatus.ACCEPTED_BY_PROVIDER) {
            paymentStateMachine.requireReconciliation(intent, failureCode, failureMessage);
        }
        updateOutboxProviderState(outbox, result, "UNKNOWN", failureCode + ": " + failureMessage);
    }

    private void refundAndFail(
            PaymentIntentEntity intent,
            PaymentExecutionOutboxEntity outbox,
            PaymentRailStatusClient.StatusResult result) {
        Long walletId = intent.getLockedWalletId();
        if (walletId == null) {
            walletId = walletRepository.findByUserId(intent.getSenderUserId()).stream()
                    .filter(wallet -> Boolean.TRUE.equals(wallet.getIsActive()))
                    .map(WalletEntity::getId)
                    .findFirst()
                    .orElse(null);
        }
        if (walletId != null) {
            ledgerService.updateBalance(
                    walletId,
                    satsToBtc(intent.getTotalDebitSats()),
                    "PAYMENT_EXTERNAL_REFUND:" + intent.getId());
            paymentAuditService.record(intent.getSenderUserId(), intent.getId(), "BALANCE_UNLOCKED", Map.of(
                    "walletId", walletId,
                    "amountSats", intent.getTotalDebitSats()));
        }

        String failureCode = firstNonBlank(result.failureCode(), "PAYMENT_RECONCILIATION_FAILED_FINAL");
        String failureMessage = firstNonBlank(
                result.safeFailureMessage(),
                "O provider confirmou falha final. O saldo foi devolvido.");
        paymentStateMachine.fail(intent, failureCode, failureMessage);
        updateOutboxProviderState(outbox, result, "FAILED_FINAL", failureCode + ": " + failureMessage);
        paymentAuditService.record(intent.getSenderUserId(), intent.getId(), "PAYMENT_FAILED", Map.of(
                "failureCode", failureCode,
                "rail", intent.getRail().name()));
    }

    private void updateOutboxProviderState(
            PaymentExecutionOutboxEntity outbox,
            PaymentRailStatusClient.StatusResult result,
            String status,
            String lastError) {
        if (outbox == null) {
            return;
        }
        outbox.setStatus(status);
        outbox.setProviderReference(firstNonBlank(result.providerReference(), outbox.getProviderReference()));
        outbox.setLastError(lastError);
        clearClaim(outbox);
        if ("SETTLED".equals(status)) {
            outbox.setDispatchedAt(outbox.getDispatchedAt() != null ? outbox.getDispatchedAt() : Instant.now());
        }
        if ("UNKNOWN".equals(status)) {
            outbox.setAttempts(outbox.getAttempts() + 1);
            outbox.setNextAttemptAt(Instant.now().plusSeconds(Math.min(3600, 60L << Math.min(outbox.getAttempts(), 5))));
        }
    }

    private String providerReference(PaymentExecutionOutboxEntity outbox) {
        return outbox != null ? firstNonBlank(outbox.getProviderReference(), "") : "";
    }

    private void clearClaim(PaymentExecutionOutboxEntity outbox) {
        outbox.setClaimedBy(null);
        outbox.setClaimedAt(null);
    }

    private BigDecimal satsToBtc(long sats) {
        return BigDecimal.valueOf(sats).divide(SATS_PER_BTC, 8, RoundingMode.HALF_UP);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
