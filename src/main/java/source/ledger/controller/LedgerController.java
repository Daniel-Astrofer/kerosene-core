package source.ledger.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import source.auth.AuthExceptions;
import source.ledger.dto.LedgerDTO;
import source.ledger.dto.PaymentRequestPublicDTO;
import source.ledger.dto.TransactionDTO;
import source.ledger.dto.InternalPaymentRequestDTO;
import source.ledger.entity.LedgerEntity;
import source.ledger.entity.LedgerTransactionHistory;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.orchestrator.TransactionContract;
import source.ledger.service.LedgerService;
import source.ledger.repository.LedgerTransactionHistoryRepository;
import source.ledger.service.LedgerPaymentRequestService;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletService;
import source.common.dto.ApiResponse;

import java.math.BigDecimal;
import java.util.List;
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
    private static final int TX_RATE_LIMIT = 10;
    private static final String TX_RATE_PREFIX = "ledger_tx_rl:";

    private final LedgerService ledgerService;
    private final WalletService walletService;
    private final TransactionContract transaction;
    private final LedgerPaymentRequestService paymentRequestService;
    private final LedgerTransactionHistoryRepository historyRepository;
    private final StringRedisTemplate redisTemplate;

    public LedgerController(LedgerService ledgerService, WalletService walletService, TransactionContract transaction,
            LedgerPaymentRequestService paymentRequestService, LedgerTransactionHistoryRepository historyRepository,
            StringRedisTemplate redisTemplate) {
        this.ledgerService = ledgerService;
        this.walletService = walletService;
        this.transaction = transaction;
        this.paymentRequestService = paymentRequestService;
        this.historyRepository = historyRepository;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/transaction")
    public ResponseEntity<ApiResponse<Void>> transaction(@RequestBody TransactionDTO dto) {
        Long userId = getAuthenticatedUserId();
        enforceTxRateLimit(userId);

        // The Transaction orchestrator resolves sender wallet from the JWT userId
        // (SecurityContextHolder), so the dto.sender field is only used as a hint
        // for which of the authenticated user's OWN wallets to send from.
        // Ownership is fully enforced inside Transaction.resolveSenderWallet().
        LogContext.timed("PROCESS_TRANSACTION", () -> transaction.processTransaction(dto));
        return ResponseEntity
                .ok(ApiResponse.success("Transaction successfully processed and ledger has been updated.", null));
    }

    /**
     * @param page 0-based page index (default 0)
     * @param size page size, capped at 100 (default 50)
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<LedgerTransactionHistory>>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Long userId = getAuthenticatedUserId();
        int safeSize = Math.min(size, 100); // hard-cap to prevent huge queries
        List<LedgerTransactionHistory> history = LogContext.timed("FETCH_HISTORY",
                () -> historyRepository.findUserHistory(userId, PageRequest.of(page, safeSize)));
        log.info("History fetched: {} entries for userId={}", history.size(), userId);
        return ResponseEntity
                .ok(ApiResponse.success("Transaction history retrieved successfully.", history));
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
        WalletEntity wallet = walletService.findByNameAndUserId(walletName, userId);
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
        WalletEntity wallet = walletService.findByNameAndUserId(walletName, userId);
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

    public record CreatePaymentRequestReq(BigDecimal amount, String receiverWalletName) {
    }

    public record PayPaymentRequestReq(String payerWalletName) {
    }

    @PostMapping("/payment-request")
    public ResponseEntity<ApiResponse<InternalPaymentRequestDTO>> createPaymentRequest(
            @RequestBody CreatePaymentRequestReq req) {
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
            @PathVariable String linkId, @RequestBody PayPaymentRequestReq req) {
        Long userId = getAuthenticatedUserId();
        enforceTxRateLimit(userId);
        InternalPaymentRequestDTO dto = paymentRequestService.payRequest(linkId, userId, req.payerWalletName());
        return ResponseEntity.ok(ApiResponse.success("Payment successful.", dto));
    }

}
