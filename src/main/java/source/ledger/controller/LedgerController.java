package source.ledger.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import source.auth.AuthExceptions;
import source.ledger.dto.LedgerDTO;
import source.ledger.dto.InternalTransactionResponseDTO;
import source.ledger.dto.PaymentRequestPublicDTO;
import source.ledger.dto.TransactionDTO;
import source.ledger.dto.InternalPaymentRequestDTO;
import source.ledger.entity.LedgerEntity;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.orchestrator.TransactionContract;
import source.ledger.service.LedgerService;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.ledger.repository.LedgerSyncEventView;
import source.ledger.service.LedgerPaymentRequestService;
import source.wallet.model.WalletEntity;
import source.wallet.application.port.in.WalletLookupPort;
import source.common.dto.ApiResponse;
import source.common.infra.logging.LogSanitizer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import source.config.LogContext;

@RestController
@RequestMapping("/ledger")
public class LedgerController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LedgerController.class);

    /**
     * Rate limit: max 10 financial operations per user per minute.
     * Applies to /transaction and /payment-request/{linkId}/pay.
     */
    private static final int TX_RATE_LIMIT = 3;
    private static final String TX_RATE_PREFIX = "ledger_tx_rl:";

    private final LedgerService ledgerService;
    private final WalletLookupPort walletLookupPort;
    private final TransactionContract transaction;
    private final LedgerPaymentRequestService paymentRequestService;
    private final LedgerTransactionHistoryRepository historyRepository;
    private final StringRedisTemplate redisTemplate;

    public LedgerController(LedgerService ledgerService, WalletLookupPort walletLookupPort, TransactionContract transaction,
            LedgerPaymentRequestService paymentRequestService, LedgerTransactionHistoryRepository historyRepository,
            StringRedisTemplate redisTemplate) {
        this.ledgerService = ledgerService;
        this.walletLookupPort = walletLookupPort;
        this.transaction = transaction;
        this.paymentRequestService = paymentRequestService;
        this.historyRepository = historyRepository;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/transaction")
    public ResponseEntity<ApiResponse<InternalTransactionResponseDTO>> transaction(@Valid @RequestBody TransactionDTO dto) {
        Long userId = getAuthenticatedUserId();
        enforceTxRateLimit(userId);

        // The Transaction orchestrator resolves sender wallet from the JWT userId
        // (SecurityContextHolder), so the dto.sender field is only used as a hint
        // for which of the authenticated user's OWN wallets to send from.
        // Ownership is fully enforced inside Transaction.resolveSenderWallet().
        LogContext.timed("PROCESS_TRANSACTION", () -> transaction.processTransaction(dto));
        String txid = dto.getIdempotencyKey() != null && !dto.getIdempotencyKey().isBlank()
                ? dto.getIdempotencyKey()
                : UUID.randomUUID().toString();
        InternalTransactionResponseDTO response = new InternalTransactionResponseDTO(
                txid,
                "confirmed",
                dto.getAmount(),
                dto.getSender(),
                dto.getReceiver(),
                dto.getContext());
        return ResponseEntity
                .ok(ApiResponse.success("Transaction successfully processed and ledger has been updated.", response));
    }

    /**
     * @param page 0-based page index (default 0)
     * @param size page size, capped at 100 (default 50)
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<LedgerSyncEventDTO>>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long userId = getAuthenticatedUserId();
        int safeSize = Math.min(size, 100); // hard-cap to prevent huge queries
        List<LedgerSyncEventDTO> history = LogContext.timed("FETCH_HISTORY",
                () -> historyRepository.findUserHistoryView(userId, PageRequest.of(page, safeSize))
                        .stream()
                        .map(historyItem -> toSyncEvent(historyItem, userId))
                        .toList());
        log.info("History fetched: {} entries for userId={}", history.size(), userId);
        return ResponseEntity
                .ok(ApiResponse.success("Ephemeral sync events retrieved successfully.", history));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<LedgerDTO>>> getAllLedgers() {
        Long userId = getAuthenticatedUserId();
        List<LedgerEntity> ledgers = ledgerService.findByUserId(userId);
        List<LedgerDTO> dtos = ledgerService.toDTOList(ledgers);
        return ResponseEntity
                .ok(ApiResponse.success("Successfully retrieved all ledgers associated with your account.", dtos));
    }

    @GetMapping("/find")
    public ResponseEntity<ApiResponse<LedgerDTO>> getLedgerByWalletName(@RequestParam String walletName) {
        Long userId = getAuthenticatedUserId();

        // Scoped lookup: finds wallet only if it belongs to the authenticated user
        WalletEntity wallet = walletLookupPort.findByNameAndUserId(walletName, userId);
        if (wallet == null) {
            throw new LedgerExceptions.LedgerNotFoundException("Wallet not found.");
        }

        LedgerEntity ledger = ledgerService.findByWalletId(wallet.getId());
        LedgerDTO dto = ledgerService.toDTO(ledger);
        return ResponseEntity.ok(ApiResponse.success("Ledger details successfully retrieved.", dto));
    }

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance(@RequestParam String walletName) {
        Long userId = getAuthenticatedUserId();

        // Scoped lookup: finds wallet only if it belongs to the authenticated user
        WalletEntity wallet = walletLookupPort.findByNameAndUserId(walletName, userId);
        if (wallet == null) {
            throw new LedgerExceptions.LedgerNotFoundException("Wallet not found.");
        }

        BigDecimal balance = ledgerService.getBalance(wallet.getId());
        return ResponseEntity.ok(ApiResponse.success("Current balance successfully retrieved.", balance));
    }

    /**
     * Safely extracts the authenticated user's ID from the Security Context.
     * Returns 401 if the principal is not a valid numeric user ID.
     */
    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthExceptions.InvalidCredentials("Not authenticated.");
        }
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            log.error("Security context contains a non-numeric principal: {}", auth.getName());
            throw new AuthExceptions.InvalidCredentials("Invalid authentication context.");
        }
    }

    /**
     * Enforces a per-user rate limit on financial operations to prevent DoS and
     * race-condition abuse on the ledger.
     */
    private void enforceTxRateLimit(Long userId) {
        String key = TX_RATE_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }
        if (count != null && count > TX_RATE_LIMIT) {
            log.warn("Ledger TX rate limit exceeded for userId={}", userId);
            throw new LedgerExceptions.TransactionReplayException(
                    "Muitas operações financeiras em pouco tempo. Aguarde um momento e tente novamente.");
        }
    }

    private LedgerSyncEventDTO toSyncEvent(LedgerSyncEventView history, Long currentUserId) {
        return new LedgerSyncEventDTO(
                history.getId(),
                history.getTransactionType(),
                history.getAmount(),
                history.getStatus(),
                currentUserId,
                history.getSenderUserId(),
                history.getReceiverUserId(),
                history.getNetworkFee(),
                LogSanitizer.fingerprint(history.getBlockchainTxid()),
                history.getCreatedAt(),
                history.getConfirmations());
    }

    /**
     * Authenticated mobile sync view for the short-lived legacy ledger buffer.
     *
     * It intentionally omits sender/receiver identifiers, free-form context and
     * full txid. It includes only user ids needed by the mobile client to infer
     * debit/credit direction. Durable readable history remains encrypted on the
     * mobile client; backend audit continuity uses hash-chain/Merkle proofs.
     */
    public record LedgerSyncEventDTO(
            UUID id,
            String transactionType,
            BigDecimal amount,
            String status,
            Long currentUserId,
            Long senderUserId,
            Long receiverUserId,
            BigDecimal networkFee,
            String txidFingerprint,
            LocalDateTime createdAt,
            Integer confirmations) {
    }

    public record CreatePaymentRequestReq(
            @NotNull(message = "amount is required")
            @DecimalMin(value = "0.00000001", message = "amount must be greater than zero")
            @DecimalMax(value = "21000000.00000000", message = "amount exceeds the maximum supported BTC amount")
            @Digits(integer = 8, fraction = 8, message = "amount must use BTC precision with at most 8 decimal places")
            BigDecimal amount,
            @NotBlank(message = "receiverWalletName is required")
            String receiverWalletName) {
    }

    public record PayPaymentRequestReq(
            @NotBlank(message = "idempotencyKey is required")
            String idempotencyKey,
            String payerWalletName,
            String totpCode,
            String passkeyAssertionJson,
            String confirmationPassphrase) {
    }

    @PostMapping("/payment-request")
    public ResponseEntity<ApiResponse<InternalPaymentRequestDTO>> createPaymentRequest(
            @Valid @RequestBody CreatePaymentRequestReq req) {
        Long userId = getAuthenticatedUserId();
        InternalPaymentRequestDTO dto = paymentRequestService.createRequest(userId, req.amount(),
                req.receiverWalletName());
        return ResponseEntity.ok(ApiResponse.success("Payment request link created successfully.", dto));
    }

    @GetMapping("/payment-request/{linkId}")
    public ResponseEntity<ApiResponse<PaymentRequestPublicDTO>> getPaymentRequest(@PathVariable String linkId) {
        InternalPaymentRequestDTO internal = paymentRequestService.getRequest(linkId);
        if (internal == null) {
            throw new LedgerExceptions.LedgerNotFoundException("Payment request not found or expired.");
        }
        // Return only the public view — strips requesterUserId and receiverWalletName
        return ResponseEntity
                .ok(ApiResponse.success("Payment request retrieved.", new PaymentRequestPublicDTO(internal)));
    }

    @PostMapping("/payment-request/{linkId}/pay")
    public ResponseEntity<ApiResponse<InternalPaymentRequestDTO>> payPaymentRequest(
            @PathVariable String linkId, @Valid @RequestBody PayPaymentRequestReq req) {
        Long userId = getAuthenticatedUserId();
        enforceTxRateLimit(userId);
        InternalPaymentRequestDTO dto = paymentRequestService.payRequest(
                linkId,
                userId,
                req.payerWalletName(),
                req.idempotencyKey(),
                req.totpCode(),
                req.passkeyAssertionJson(),
                req.confirmationPassphrase());
        return ResponseEntity.ok(ApiResponse.success("Payment successful.", dto));
    }

}
