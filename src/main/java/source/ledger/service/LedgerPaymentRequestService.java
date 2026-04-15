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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
        req.setReceiverWalletId(wallet.getId());
        req.setReceiverWalletName(wallet.getName());
        req.setDestinationHash(buildDestinationHash(wallet));
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
            history.setReceiverIdentifier(wallet.getName());
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
                            amount.toPlainString(), wallet.getName()));
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

        if ("PENDING".equals(req.getStatus())
                && (req.getReceiverWalletId() == null
                        || req.getDestinationHash() == null
                        || req.getDestinationHash().isBlank())) {
            resolveLockedReceiverWallet(req);
            redisTemplate.opsForValue().set(key, req, TTL_MINUTES, TimeUnit.MINUTES);
        }

        return req;
    }

    public InternalPaymentRequestDTO payRequest(
            String linkId,
            Long payerUserId,
            String payerWalletName,
            String totpCode,
            String passkeyAssertionJson,
            String confirmationPassphrase) {
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

        WalletEntity receiverWallet = resolveLockedReceiverWallet(req);

        // Delegate to the standard transaction orchestrator to move funds safely
        TransactionDTO txDto = new TransactionDTO();
        txDto.setSender(payerWalletName);
        // The receiver is the immutable wallet captured when the link was created.
        // The client never sends this value back, so shared links and QR codes cannot
        // redirect funds by changing a visible wallet name or destination field.
        txDto.setReceiver(receiverWallet.getId().toString());
        txDto.setAmount(req.getAmount());
        txDto.setContext("Payment Link " + linkId);
        txDto.setTotpCode(totpCode);
        txDto.setPasskeyAssertionJson(passkeyAssertionJson);
        txDto.setConfirmationPassphrase(confirmationPassphrase);

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

    private WalletEntity resolveLockedReceiverWallet(InternalPaymentRequestDTO req) {
        WalletEntity wallet = null;
        if (req.getReceiverWalletId() != null) {
            wallet = walletService.findById(req.getReceiverWalletId());
        }

        if (wallet == null && req.getReceiverWalletName() != null && req.getRequesterUserId() != null) {
            wallet = walletService.findByNameAndUserId(req.getReceiverWalletName(), req.getRequesterUserId());
        }

        if (wallet == null) {
            throw new LedgerExceptions.ReceiverNotFoundException(
                    "Receiver wallet locked in this payment request was not found.");
        }

        if (!wallet.getUser().getId().equals(req.getRequesterUserId())) {
            throw new LedgerExceptions.ReceiverNotFoundException(
                    "Receiver wallet locked in this payment request no longer belongs to the requester.");
        }

        if (req.getReceiverWalletId() == null) {
            req.setReceiverWalletId(wallet.getId());
        }
        if (req.getDestinationHash() == null || req.getDestinationHash().isBlank()) {
            req.setDestinationHash(buildDestinationHash(wallet));
        }

        return wallet;
    }

    private String buildDestinationHash(WalletEntity wallet) {
        String source = firstNonBlank(
                wallet.getDepositAddress(),
                wallet.getPassphraseHash(),
                wallet.getId() == null ? null : "wallet:" + wallet.getId());
        if (source == null) {
            source = "wallet:unknown";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate payment destination hash", e);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
