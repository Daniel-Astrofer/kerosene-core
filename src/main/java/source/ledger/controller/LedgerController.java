package source.ledger.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import source.auth.application.service.cripto.contracts.Hasher;
import source.ledger.dto.LedgerDTO;
import source.ledger.dto.TransactionDTO;
import source.ledger.entity.LedgerEntity;
import source.ledger.orchestrator.TransactionContract;
import source.ledger.service.LedgerService;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletService;

import java.math.BigDecimal;
import java.util.List;


@RestController
@RequestMapping("/ledger")
public class LedgerController {

    private final LedgerService ledgerService;
    private final WalletService walletService;
    private final TransactionContract transaction;


    public LedgerController(LedgerService ledgerService, WalletService walletService, TransactionContract transaction) {
        this.ledgerService = ledgerService;
        this.walletService = walletService;

        this.transaction = transaction;
    }


    @PostMapping("/transaction")
    public ResponseEntity<LedgerDTO> transaction(@RequestBody TransactionDTO dto, HttpServletRequest request) {
        transaction.processTransaction(dto);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }


    @GetMapping("/all")
    public ResponseEntity<List<LedgerDTO>> getAllLedgers(HttpServletRequest request) {
        Long userId = getAuthenticatedUserId();
        
        List<LedgerEntity> ledgers = ledgerService.findByUserId(userId);
        List<LedgerDTO> dtos = ledgerService.toDTOList(ledgers);
        
        return ResponseEntity.ok(dtos);
    }


    @GetMapping("/find")
    public ResponseEntity<LedgerDTO> getLedgerByWalletName(@RequestParam String walletName, HttpServletRequest request) {
        Long userId = getAuthenticatedUserId();
        
        WalletEntity wallet = walletService.findByName(walletName);
        ledgerService.validateWalletOwnership(wallet, userId);
        
        LedgerEntity ledger = ledgerService.findByWalletId(wallet.getId());
        LedgerDTO dto = ledgerService.toDTO(ledger);
        
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/balance")
    public ResponseEntity<BigDecimal> getBalance(@RequestParam String walletName, HttpServletRequest request) {
        Long userId = getAuthenticatedUserId();
        
        WalletEntity wallet = walletService.findByName(walletName);
        ledgerService.validateWalletOwnership(wallet, userId);
        
        BigDecimal balance = ledgerService.getBalance(wallet.getId());
        
        return ResponseEntity.ok(balance);
    }


    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteLedger(@RequestParam String walletName, HttpServletRequest request) {
        Long userId = getAuthenticatedUserId();
        
        WalletEntity wallet = walletService.findByName(walletName);

        ledgerService.validateWalletOwnership(wallet, userId);
        
        ledgerService.deleteLedger(wallet.getId());
        
        return ResponseEntity.ok("Ledger deleted successfully");
    }


    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(auth.getName());
    }


}
