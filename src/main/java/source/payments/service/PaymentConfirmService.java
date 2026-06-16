package source.payments.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.infra.logging.LogSanitizer;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.service.LedgerContract;
import source.payments.dto.PaymentConfirmRequest;
import source.payments.dto.PaymentStatusResponse;
import source.payments.exception.PaymentException;
import source.payments.model.PaymentEnums;
import source.payments.model.PaymentIntentEntity;
import source.payments.repository.PaymentIntentRepository;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentConfirmService {

    private static final BigDecimal SATS_PER_BTC = new BigDecimal("100000000");

    private final PaymentIntentRepository paymentIntentRepository;
    private final WalletRepository walletRepository;
    private final LedgerContract ledgerService;
    private final PaymentAuditService paymentAuditService;
    private final PaymentResponseMapper responseMapper;
    private final PaymentStateMachine paymentStateMachine;
    private final PaymentExecutionOutboxService paymentExecutionOutboxService;

    public PaymentConfirmService(
            PaymentIntentRepository paymentIntentRepository,
            WalletRepository walletRepository,
            LedgerContract ledgerService,
            PaymentAuditService paymentAuditService,
            PaymentResponseMapper responseMapper,
            PaymentStateMachine paymentStateMachine,
            PaymentExecutionOutboxService paymentExecutionOutboxService) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.walletRepository = walletRepository;
        this.ledgerService = ledgerService;
        this.paymentAuditService = paymentAuditService;
        this.responseMapper = responseMapper;
        this.paymentStateMachine = paymentStateMachine;
        this.paymentExecutionOutboxService = paymentExecutionOutboxService;
    }

    @Transactional
    public PaymentStatusResponse confirm(Long senderUserId, UUID paymentIntentId, PaymentConfirmRequest request) {
        PaymentIntentEntity intent = paymentIntentRepository.findByIdAndSenderUserIdForUpdate(paymentIntentId, senderUserId)
                .orElseThrow(() -> PaymentException.notFound(
                        "PAYMENT_INTENT_NOT_FOUND",
                        "Não encontramos esta cotação."));

        validateIdempotencyKey(intent, request.idempotencyKey());
        if (paymentStateMachine.isTerminal(intent)) {
            return responseMapper.toStatusResponse(intent);
        }
        if (intent.getIdempotencyKey() != null
                && intent.getIdempotencyKey().equals(request.idempotencyKey())
                && paymentStateMachine.isInFlight(intent)) {
            return responseMapper.toStatusResponse(intent);
        }
        validatePaymentQuote(intent, request);

        intent.setIdempotencyKey(request.idempotencyKey());
        paymentStateMachine.confirm(intent);
        paymentAuditService.record(senderUserId, intent.getId(), "PAYMENT_CONFIRMED", java.util.Map.of(
                "rail", intent.getRail().name(),
                "totalDebitSats", intent.getTotalDebitSats(),
                "receiverAmountSats", intent.getReceiverAmountSats()));

        if (intent.getRail() != PaymentEnums.PaymentRail.INTERNAL) {
            return enqueueExternalPayment(senderUserId, intent, request.idempotencyKey());
        }

        settleInternalTransfer(senderUserId, intent);
        PaymentIntentEntity saved = paymentIntentRepository.save(intent);
        return responseMapper.toStatusResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse status(Long senderUserId, UUID paymentIntentId) {
        PaymentIntentEntity intent = paymentIntentRepository.findByIdAndSenderUserId(paymentIntentId, senderUserId)
                .orElseThrow(() -> PaymentException.notFound(
                        "PAYMENT_INTENT_NOT_FOUND",
                        "Não encontramos este envio."));
        return responseMapper.toStatusResponse(intent);
    }

    private void validateIdempotencyKey(PaymentIntentEntity intent, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw PaymentException.badRequest(
                    "PAYMENT_IDEMPOTENCY_KEY_REQUIRED",
                    "Não foi possível confirmar com segurança. Tente novamente.");
        }
        if (intent.getIdempotencyKey() != null && !intent.getIdempotencyKey().equals(idempotencyKey)) {
            throw PaymentException.conflict(
                    "PAYMENT_IDEMPOTENCY_KEY_MISMATCH",
                    "Esta confirmação já foi iniciada com outra chave de segurança.");
        }
        paymentIntentRepository.findByIdempotencyKey(idempotencyKey)
                .filter(existing -> !existing.getId().equals(intent.getId()))
                .ifPresent(existing -> {
                    throw PaymentException.conflict(
                            "PAYMENT_IDEMPOTENCY_KEY_REUSED",
                            "Esta confirmação já foi enviada. Aguarde o processamento.");
                });
    }

    private void validatePaymentQuote(PaymentIntentEntity intent, PaymentConfirmRequest request) {
        if (intent.getStatus() != PaymentEnums.PaymentIntentStatus.QUOTED) {
            throw PaymentException.conflict(
                    "PAYMENT_INTENT_NOT_CONFIRMABLE",
                    "Este envio não está mais disponível para confirmação.");
        }
        if (intent.getQuoteExpiresAt().isBefore(Instant.now())) {
            paymentStateMachine.expire(intent, "QUOTE_EXPIRED", "A cotação expirou. Gere uma nova antes de confirmar.");
            paymentIntentRepository.save(intent);
            throw PaymentException.conflict(
                    "QUOTE_EXPIRED",
                    "A cotação expirou. Gere uma nova antes de confirmar.");
        }
        if (!intent.getTotalDebitSats().equals(request.acceptedTotalDebitSats())
                || !intent.getReceiverAmountSats().equals(request.acceptedReceiverAmountSats())) {
            throw PaymentException.conflict(
                    "QUOTE_CHANGED",
                    "A cotação mudou. Revise os valores antes de confirmar.");
        }
    }

    private PaymentStatusResponse enqueueExternalPayment(Long senderUserId, PaymentIntentEntity intent, String idempotencyKey) {
        WalletEntity senderWallet = resolvePrimaryWallet(senderUserId);
        intent.setLockedWalletId(senderWallet.getId());
        paymentStateMachine.startProcessing(intent);
        paymentAuditService.record(senderUserId, intent.getId(), "BALANCE_LOCKED", java.util.Map.of(
                "walletId", senderWallet.getId(),
                "amountSats", intent.getTotalDebitSats()));

        try {
            ledgerService.updateBalance(
                    senderWallet.getId(),
                    convertSatoshisToBitcoin(intent.getTotalDebitSats()).negate(),
                    buildLedgerContext("PAYMENT_EXTERNAL_LOCK", intent));
        } catch (LedgerExceptions.InsufficientBalanceException exception) {
            throw PaymentException.badRequest(
                    "PAYMENT_INSUFFICIENT_FUNDS",
                    "Saldo insuficiente para cobrir o valor e as taxas.");
        }

        paymentExecutionOutboxService.enqueue(intent, idempotencyKey);
        paymentAuditService.record(senderUserId, intent.getId(), "PAYMENT_EXTERNAL_QUEUED", java.util.Map.of(
                "rail", intent.getRail().name(),
                "totalDebitSats", intent.getTotalDebitSats()));
        PaymentIntentEntity saved = paymentIntentRepository.save(intent);
        return responseMapper.toStatusResponse(saved);
    }

    private void settleInternalTransfer(Long senderUserId, PaymentIntentEntity intent) {
        if (intent.getReceiverUserId() == null) {
            throw PaymentException.badRequest(
                    "RECEIVER_NOT_READY",
                    "Este usuário ainda não está pronto para receber fundos.");
        }
        WalletEntity senderWallet = resolvePrimaryWallet(senderUserId);
        WalletEntity receiverWallet = resolvePrimaryWallet(intent.getReceiverUserId());

        paymentStateMachine.startProcessing(intent);
        paymentAuditService.record(senderUserId, intent.getId(), "BALANCE_LOCKED", java.util.Map.of(
                "walletId", senderWallet.getId(),
                "amountSats", intent.getTotalDebitSats()));

        BigDecimal receiverAmountBtc = convertSatoshisToBitcoin(intent.getReceiverAmountSats());
        BigDecimal totalDebitBtc = convertSatoshisToBitcoin(intent.getTotalDebitSats()).negate();
        try {
            ledgerService.updateBalance(senderWallet.getId(), totalDebitBtc, buildLedgerContext("PAYMENT_INTERNAL_DEBIT", intent));
            ledgerService.updateBalance(receiverWallet.getId(), receiverAmountBtc, buildLedgerContext("PAYMENT_INTERNAL_CREDIT", intent));
        } catch (LedgerExceptions.InsufficientBalanceException exception) {
            throw PaymentException.badRequest(
                    "PAYMENT_INSUFFICIENT_FUNDS",
                    "Saldo insuficiente para cobrir o valor e as taxas.");
        }

        paymentStateMachine.settle(intent);
        paymentAuditService.record(senderUserId, intent.getId(), "INTERNAL_TRANSFER_SETTLED", java.util.Map.of(
                "senderWalletId", senderWallet.getId(),
                "receiverWalletId", receiverWallet.getId(),
                "receiverAmountSats", intent.getReceiverAmountSats()));
    }

    private WalletEntity resolvePrimaryWallet(Long userId) {
        List<WalletEntity> wallets = walletRepository.findByUserId(userId).stream()
                .filter(wallet -> Boolean.TRUE.equals(wallet.getIsActive()))
                .sorted(Comparator
                        .comparing((WalletEntity wallet) -> !"MAIN".equalsIgnoreCase(wallet.getName()))
                        .thenComparing(WalletEntity::getId))
                .toList();
        if (wallets.isEmpty()) {
            throw PaymentException.badRequest(
                    "PAYMENT_WALLET_NOT_READY",
                    "A carteira desta conta ainda não está pronta para movimentações.");
        }
        return wallets.get(0);
    }

    private BigDecimal convertSatoshisToBitcoin(long sats) {
        return BigDecimal.valueOf(sats).divide(SATS_PER_BTC, 8, RoundingMode.HALF_UP);
    }

    private String buildLedgerContext(String operation, PaymentIntentEntity intent) {
        return operation
                + ":paymentIntent=" + intent.getId()
                + ":idem=" + LogSanitizer.fingerprint(intent.getIdempotencyKey());
    }
}
