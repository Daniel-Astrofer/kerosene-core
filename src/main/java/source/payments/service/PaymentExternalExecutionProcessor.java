package source.payments.service;

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

@Service
public class PaymentExternalExecutionProcessor {

    private static final BigDecimal SATS_PER_BTC = new BigDecimal("100000000");

    private final PaymentExecutionOutboxRepository outboxRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final WalletRepository walletRepository;
    private final LedgerContract ledgerService;
    private final PaymentAuditService paymentAuditService;
    private final PaymentStateMachine paymentStateMachine;
    private final Map<PaymentEnums.PaymentRail, PaymentRailExecutor> executorsByRail;

    public PaymentExternalExecutionProcessor(
            PaymentExecutionOutboxRepository outboxRepository,
            PaymentIntentRepository paymentIntentRepository,
            WalletRepository walletRepository,
            LedgerContract ledgerService,
            PaymentAuditService paymentAuditService,
            PaymentStateMachine paymentStateMachine,
            List<PaymentRailExecutor> executors) {
        this.outboxRepository = outboxRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.walletRepository = walletRepository;
        this.ledgerService = ledgerService;
        this.paymentAuditService = paymentAuditService;
        this.paymentStateMachine = paymentStateMachine;
        this.executorsByRail = executors.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                PaymentRailExecutor::rail,
                executor -> executor,
                (left, right) -> left));
    }

    @Transactional
    public void process(UUID outboxId) {
        PaymentExecutionOutboxEntity outbox = outboxRepository.findByIdForUpdate(outboxId).orElse(null);
        if (outbox == null || !isClaimedForProcessing(outbox)) {
            return;
        }

        PaymentIntentEntity intent = paymentIntentRepository.findByIdForUpdate(outbox.getPaymentIntentId())
                .orElse(null);
        if (intent == null || intent.getStatus() != PaymentEnums.PaymentIntentStatus.PROCESSING) {
            markFinal(outbox, "PAYMENT_INTENT_NOT_PROCESSING", "O envio nao esta mais em processamento.");
            return;
        }

        Optional<PaymentRailExecutor> executor = Optional.ofNullable(executorsByRail.get(intent.getRail()));
        if (executor.isEmpty()) {
            refundAndFail(
                    intent,
                    outbox,
                    "PAYMENT_RAIL_EXECUTION_NOT_CONFIGURED",
                    "Este metodo de envio ainda nao possui executor seguro configurado.");
            return;
        }

        try {
            PaymentRailExecutor.ExecutionResult result = executor.get().execute(intent);
            applyExecutionResult(intent, outbox, result);
            paymentAuditService.record(intent.getSenderUserId(), intent.getId(), eventTypeFor(intent), Map.of(
                    "providerReference", result.providerReference() != null ? result.providerReference() : "",
                    "providerStatus", result.providerStatus() != null ? result.providerStatus() : "",
                    "outcome", result.outcome().name(),
                    "rail", intent.getRail().name()));
        } catch (RuntimeException exception) {
            markUnknown(
                    intent,
                    outbox,
                    "PAYMENT_EXTERNAL_EXECUTION_UNKNOWN",
                    "O envio externo ficou pendente de reconciliacao apos uma falha temporaria.");
        }
    }

    private void applyExecutionResult(
            PaymentIntentEntity intent,
            PaymentExecutionOutboxEntity outbox,
            PaymentRailExecutor.ExecutionResult result) {
        switch (result.outcome()) {
            case ACCEPTED -> {
                markDispatched(outbox, result);
                paymentStateMachine.acceptByProvider(intent);
            }
            case SETTLED -> {
                markSettled(outbox, result);
                paymentStateMachine.settle(intent);
            }
            case FAILED_RETRYABLE -> markRetryable(
                    outbox,
                    firstNonBlank(result.failureCode(), "PAYMENT_EXTERNAL_EXECUTION_RETRYABLE"),
                    firstNonBlank(result.safeFailureMessage(), "O envio externo sera tentado novamente."));
            case FAILED_FINAL -> refundAndFail(
                    intent,
                    outbox,
                    firstNonBlank(result.failureCode(), "PAYMENT_EXTERNAL_EXECUTION_FAILED"),
                    firstNonBlank(result.safeFailureMessage(), "Nao foi possivel concluir este envio. O saldo foi devolvido."));
            case UNKNOWN -> markUnknown(
                    intent,
                    outbox,
                    result,
                    "PAYMENT_EXTERNAL_EXECUTION_UNKNOWN",
                    "O envio externo precisa de reconciliacao antes do estado final.");
        }
    }

    private void markDispatched(
            PaymentExecutionOutboxEntity outbox,
            PaymentRailExecutor.ExecutionResult result) {
        outbox.setStatus("DISPATCHED");
        outbox.setProviderReference(result.providerReference());
        outbox.setDispatchedAt(Instant.now());
        clearClaim(outbox);
        outbox.setLastError(null);
    }

    private void markSettled(
            PaymentExecutionOutboxEntity outbox,
            PaymentRailExecutor.ExecutionResult result) {
        outbox.setStatus("SETTLED");
        outbox.setProviderReference(result.providerReference());
        outbox.setDispatchedAt(Instant.now());
        clearClaim(outbox);
        outbox.setLastError(null);
    }

    private void markRetryable(
            PaymentExecutionOutboxEntity outbox,
            String failureCode,
            String safeFailureMessage) {
        outbox.setAttempts(outbox.getAttempts() + 1);
        outbox.setStatus("FAILED_RETRYABLE");
        outbox.setLastError(failureCode + ": " + safeFailureMessage);
        outbox.setNextAttemptAt(Instant.now().plusSeconds(Math.min(3600, 30L << Math.min(outbox.getAttempts(), 6))));
        clearClaim(outbox);
    }

    private void markUnknown(
            PaymentIntentEntity intent,
            PaymentExecutionOutboxEntity outbox,
            String failureCode,
            String safeFailureMessage) {
        markUnknown(intent, outbox, null, failureCode, safeFailureMessage);
    }

    private void markUnknown(
            PaymentIntentEntity intent,
            PaymentExecutionOutboxEntity outbox,
            PaymentRailExecutor.ExecutionResult result,
            String failureCode,
            String safeFailureMessage) {
        outbox.setAttempts(outbox.getAttempts() + 1);
        outbox.setStatus("UNKNOWN");
        if (result != null && firstNonBlank(result.providerReference()) != null) {
            outbox.setProviderReference(result.providerReference());
        }
        outbox.setLastError(failureCode + ": " + safeFailureMessage);
        outbox.setNextAttemptAt(Instant.now().plusSeconds(300));
        clearClaim(outbox);
        paymentStateMachine.requireReconciliation(intent, failureCode, safeFailureMessage);
    }

    private void refundAndFail(
            PaymentIntentEntity intent,
            PaymentExecutionOutboxEntity outbox,
            String failureCode,
            String failureMessage) {
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

        paymentStateMachine.fail(intent, failureCode, failureMessage);
        markFinal(outbox, failureCode, failureMessage);
        paymentAuditService.record(intent.getSenderUserId(), intent.getId(), "PAYMENT_FAILED", Map.of(
                "failureCode", failureCode,
                "rail", intent.getRail().name()));
    }

    private void markFinal(PaymentExecutionOutboxEntity outbox, String failureCode, String failureMessage) {
        outbox.setAttempts(outbox.getAttempts() + 1);
        outbox.setStatus("FAILED_FINAL");
        outbox.setLastError(failureCode + ": " + failureMessage);
        outbox.setNextAttemptAt(Instant.now().plusSeconds(3600));
        clearClaim(outbox);
    }

    private boolean isClaimedForProcessing(PaymentExecutionOutboxEntity outbox) {
        return "PROCESSING".equals(outbox.getStatus())
                && outbox.getClaimedBy() != null
                && outbox.getClaimedAt() != null;
    }

    private void clearClaim(PaymentExecutionOutboxEntity outbox) {
        outbox.setClaimedBy(null);
        outbox.setClaimedAt(null);
    }

    private String eventTypeFor(PaymentIntentEntity intent) {
        return intent.getRail() == PaymentEnums.PaymentRail.LIGHTNING
                ? "LIGHTNING_PAYMENT_SENT"
                : "ONCHAIN_TX_BROADCASTED";
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
        return null;
    }
}
