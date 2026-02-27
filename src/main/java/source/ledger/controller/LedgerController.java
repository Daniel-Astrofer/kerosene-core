package source.ledger.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import source.ledger.dto.LedgerDTO;
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
import org.springframework.data.domain.PageRequest;
import source.config.LogContext;

@RestController
@RequestMapping("/ledger")
public class LedgerController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LedgerController.class);

    private final LedgerService ledgerService;
    private final WalletService walletService;
    private final TransactionContract transaction;
    private final LedgerPaymentRequestService paymentRequestService;
    private final LedgerTransactionHistoryRepository historyRepository;

    public LedgerController(LedgerService ledgerService, WalletService walletService, TransactionContract transaction,
            LedgerPaymentRequestService paymentRequestService, LedgerTransactionHistoryRepository historyRepository) {
        this.ledgerService = ledgerService;
        this.walletService = walletService;
        this.transaction = transaction;
        this.paymentRequestService = paymentRequestService;
        this.historyRepository = historyRepository;
    }

    @PostMapping("/transaction")
    public ResponseEntity<ApiResponse<TransactionDTO>> transaction(@RequestBody TransactionDTO dto) {
        LogContext.timed("PROCESS_TRANSACTION", () -> transaction.processTransaction(dto));
        return ResponseEntity
                .ok(ApiResponse.success("Transaction successfully processed and ledger has been updated.", dto));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<LedgerTransactionHistory>>> getHistory() {
        Long userId = getAuthenticatedUserId();
        List<LedgerTransactionHistory> history = LogContext.timed("FETCH_HISTORY",
                () -> historyRepository.findUserHistory(userId, PageRequest.of(0, 100)));
        log.info("History fetched: {} entries", history.size());
        return ResponseEntity
                .ok(ApiResponse.success("Transaction history (last 100 entries) retrieved successfully.", history));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<LedgerDTO>>> getAllLedgers() {
        Long userId = getAuthenticatedUserId();
        log.debug("GET /ledger/all - UserId: {}", userId);

        List<LedgerEntity> ledgers = ledgerService.findByUserId(userId);
        List<LedgerDTO> dtos = ledgerService.toDTOList(ledgers);

        log.debug("Returning {} ledger entries for userId={}", dtos.size(), userId);
        return ResponseEntity
                .ok(ApiResponse.success("Successfully retrieved all ledgers associated with your account.", dtos));
    }

    @GetMapping("/find")
    public ResponseEntity<ApiResponse<LedgerDTO>> getLedgerByWalletName(@RequestParam String walletName) {
        Long userId = getAuthenticatedUserId();
        log.debug("GET /ledger/find?walletName={} - UserId: {}", walletName, userId);

        WalletEntity wallet = walletService.findByName(walletName);
        log.debug("Wallet found: ID={}", wallet != null ? wallet.getId() : "NULL");
        ledgerService.validateWalletOwnership(wallet, userId);

        LedgerEntity ledger = ledgerService.findByWalletId(wallet.getId());
        LedgerDTO dto = ledgerService.toDTO(ledger);

        log.debug("Ledger found with balance: {}", dto.getBalance());
        return ResponseEntity.ok(ApiResponse.success("Ledger details successfully retrieved.", dto));
    }

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<BigDecimal>> getBalance(@RequestParam String walletName) {
        Long userId = getAuthenticatedUserId();
        log.debug("GET /ledger/balance?walletName={} - UserId: {}", walletName, userId);

        WalletEntity wallet = walletService.findByName(walletName);
        log.debug("Wallet found: ID={}, Owner={}",
                wallet != null ? wallet.getId() : "NULL",
                wallet != null && wallet.getUser() != null ? wallet.getUser().getId() : "NULL");
        ledgerService.validateWalletOwnership(wallet, userId);

        BigDecimal balance = ledgerService.getBalance(wallet.getId());

        log.debug("Current balance for wallet {}: {}", walletName, balance);
        return ResponseEntity.ok(ApiResponse.success("Current balance successfully retrieved.", balance));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse<String>> deleteLedger(@RequestParam String walletName) {
        Long userId = getAuthenticatedUserId();

        WalletEntity wallet = walletService.findByName(walletName);

        ledgerService.validateWalletOwnership(wallet, userId);

        ledgerService.deleteLedger(wallet.getId());

        return ResponseEntity.ok(ApiResponse.success("Ledger successfully completely removed from the system."));
    }

    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
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
    public ResponseEntity<ApiResponse<InternalPaymentRequestDTO>> getPaymentRequest(@PathVariable String linkId) {
        InternalPaymentRequestDTO dto = paymentRequestService.getRequest(linkId);
        if (dto == null) {
            throw new LedgerExceptions.LedgerNotFoundException("Payment request not found or expired.");
        }
        return ResponseEntity.ok(ApiResponse.success("Payment request retrieved.", dto));
    }

    @PostMapping("/payment-request/{linkId}/pay")
    public ResponseEntity<ApiResponse<InternalPaymentRequestDTO>> payPaymentRequest(
            @PathVariable String linkId, @RequestBody PayPaymentRequestReq req) {
        Long userId = getAuthenticatedUserId();
        InternalPaymentRequestDTO dto = paymentRequestService.payRequest(linkId, userId, req.payerWalletName());
        return ResponseEntity.ok(ApiResponse.success("Payment successful.", dto));
    }

}
