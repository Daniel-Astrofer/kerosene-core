package source.ledger.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import source.auth.application.service.user.UserService;
import source.ledger.dto.InternalPaymentRequestDTO;
import source.ledger.dto.TransactionDTO;
import source.ledger.event.PaymentRequestEventPublisher;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.orchestrator.TransactionContract;
import source.notification.service.NotificationService;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletContract;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class LedgerPaymentRequestService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LedgerPaymentRequestService.class);

    private final RedisTemplate<String, InternalPaymentRequestDTO> redisTemplate;
    private final WalletContract walletService;
    private final UserService userService;
    private final TransactionContract transactionOrchestrator;
    private final NotificationService notificationService;
    private final PaymentRequestEventPublisher paymentEventPublisher;
    private final source.ledger.repository.LedgerTransactionHistoryRepository historyRepository;

    private static final String REDIS_PREFIX = "internal_payment_req:";
    private static final long TTL_MINUTES = 30;

    public LedgerPaymentRequestService(RedisTemplate<String, InternalPaymentRequestDTO> redisTemplate,
            WalletContract walletService, UserService userService, TransactionContract transactionOrchestrator,
            NotificationService notificationService,
            PaymentRequestEventPublisher paymentEventPublisher,
            source.ledger.repository.LedgerTransactionHistoryRepository historyRepository) {
        this.redisTemplate = redisTemplate;
        this.walletService = walletService;
        this.userService = userService;
        this.transactionOrchestrator = transactionOrchestrator;
        this.notificationService = notificationService;
        this.paymentEventPublisher = paymentEventPublisher;
        this.historyRepository = historyRepository;
    }

    public InternalPaymentRequestDTO createRequest(Long requesterUserId, BigDecimal amount, String receiverWalletName) {
        // Validate payment amount — must be strictly positive to prevent flow inversion
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new LedgerExceptions.InvalidAmountException(
                    "O valor da solicitação de pagamento deve ser maior que zero.");
        }

        userService.buscarPorId(requesterUserId).orElseThrow(
                () -> new RuntimeException("Requester user not found"));

        // Scoped lookup: only find the wallet if it belongs to the requester
        WalletEntity wallet = walletService.findByNameAndUserId(receiverWalletName, requesterUserId);
        if (wallet == null) {
            throw new LedgerExceptions.ReceiverNotFoundException(
                    "Receiver wallet not found or does not belong to you.");
        }

        InternalPaymentRequestDTO req = new InternalPaymentRequestDTO();
        req.setId(UUID.randomUUID().toString());
        req.setRequesterUserId(requesterUserId);
        req.setReceiverWalletName(receiverWalletName);
        req.setAmount(amount);
        req.setStatus("PENDING");
        req.setCreatedAt(LocalDateTime.now());
        req.setExpiresAt(LocalDateTime.now().plusMinutes(TTL_MINUTES));

        String key = REDIS_PREFIX + req.getId();
        redisTemplate.opsForValue().set(key, req, TTL_MINUTES, TimeUnit.MINUTES);

        // Save to explicit history
        try {
            source.ledger.entity.LedgerTransactionHistory history = new source.ledger.entity.LedgerTransactionHistory();
            history.setId(UUID.fromString(req.getId()));
            history.setAmount(amount.abs());
            history.setCreatedAt(LocalDateTime.now());
            history.setContext("Internal Payment Request: " + req.getId());
            history.setReceiverUserId(requesterUserId);
            history.setReceiverIdentifier(receiverWalletName);
            history.setTransactionType("PAYMENT_LINK");
            history.setStatus("PENDING");
            historyRepository.save(history);
        } catch (Exception e) {
            log.warn("Failed to save payment request history: {}", e.getMessage());
        }

        // Notify creator
        try {
            notificationService.notifyUser(requesterUserId, "Solicitação de Pagamento Gerada",
                    String.format("Um novo link de pagamento no valor de %s BTC foi criado para a carteira '%s'.",
                            amount.toPlainString(), receiverWalletName));
        } catch (Exception e) {
            log.warn("Payment request creation notification failed (non-blocking): {}", e.getMessage());
        }

        return req;
    }

    public InternalPaymentRequestDTO getRequest(String linkId) {
        String key = REDIS_PREFIX + linkId;
        InternalPaymentRequestDTO req = redisTemplate.opsForValue().get(key);

        if (req == null) {
            throw new LedgerExceptions.PaymentRequestNotFoundException(
                    "Payment request not found or has been completely removed.");
        }

        if ("PENDING".equals(req.getStatus()) && LocalDateTime.now().isAfter(req.getExpiresAt())) {
            req.setStatus("EXPIRED");
        }

        return req;
    }

    public InternalPaymentRequestDTO payRequest(String linkId, Long payerUserId, String payerWalletName) {
        String key = REDIS_PREFIX + linkId;
        InternalPaymentRequestDTO req = redisTemplate.opsForValue().get(key);

        if (req == null) {
            throw new LedgerExceptions.PaymentRequestNotFoundException("Payment request not found or expired.");
        }

        if ("PAID".equals(req.getStatus())) {
            throw new LedgerExceptions.PaymentRequestAlreadyPaidException(
                    "This payment request has already been successfully processed.");
        }

        if (LocalDateTime.now().isAfter(req.getExpiresAt())) {
            req.setStatus("EXPIRED");
            throw new LedgerExceptions.PaymentRequestExpiredException(
                    "This payment request has expired and can no longer be paid.");
        }

        if (!"PENDING".equals(req.getStatus())) {
            throw new RuntimeException("Payment request is in an invalid state: " + req.getStatus());
        }

        if (req.getRequesterUserId().equals(payerUserId)) {
            throw new LedgerExceptions.PaymentRequestSelfPayException(
                    "Operation Denied: You cannot pay a request that you created yourself.");
        }

        // Delegate to the standard transaction orchestrator to move funds safely
        TransactionDTO txDto = new TransactionDTO();
        txDto.setSender(payerWalletName);
        // We know the receiver is the wallet of the requester
        txDto.setReceiver(req.getReceiverWalletName());
        txDto.setAmount(req.getAmount());
        txDto.setContext("Payment Link " + linkId);

        // Process transaction (This internally verifies balance, debit, credit and
        // sends push notification)
        transactionOrchestrator.processTransaction(txDto);

        // Mark as paid
        req.setStatus("PAID");
        req.setPaidAt(LocalDateTime.now());

        // Update history record
        try {
            historyRepository.updateStatus(UUID.fromString(linkId), "CONCLUDED");
        } catch (Exception e) {
            log.warn("Failed to update payment request history status for linkId={}: {}", linkId, e.getMessage());
        }

        // Save back to redis with remaining TTL or extending if desired
        redisTemplate.opsForValue().set(key, req, 30, TimeUnit.MINUTES);

        // Notify requester that the link was paid — Firebase push
        try {
            notificationService.notifyUser(req.getRequesterUserId(), "Solicitação de Pagamento Liquidada",
                    String.format("Seu pedido de pagamento no valor de %s BTC foi processado com sucesso.",
                            req.getAmount().toPlainString()));
        } catch (Exception e) {
            log.warn("payRequest notification failed (non-blocking): {}", e.getMessage());
        }

        // Real-time WebSocket push → /topic/payment-request/{linkId}
        // The creator's client receives the full updated DTO with status=PAID
        try {
            paymentEventPublisher.publishPaymentPaid(req);
        } catch (Exception e) {
            log.warn("[WS-PAYMENT] Failed to push paid event for linkId={}: {}", linkId, e.getMessage());
        }

        return req;
    }
}
