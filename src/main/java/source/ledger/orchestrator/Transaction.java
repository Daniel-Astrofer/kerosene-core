package source.ledger.orchestrator;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import source.auth.application.service.user.UserService;
import source.auth.model.entity.UserDataBase;
import source.ledger.dto.TransactionDTO;
import source.ledger.entity.LedgerEntity;
import source.ledger.exceptions.LedgerExceptions;
import source.ledger.service.LedgerContract;
import source.ledger.service.LedgerService;
import source.wallet.model.WalletEntity;
import source.wallet.service.WalletContract;
import source.wallet.service.WalletService;

import java.math.BigDecimal;
import java.util.List;

/**
 * Orquestrador de transações entre carteiras.
 * Suporta múltiplos formatos de identificação:
 * - Username: "what", "alice", etc
 * - Wallet ID: "1", "2", etc
 * - Bitcoin Address: "1A1z7agoat7F9gq5...", "3J98t1W1mU4..."
 */
@Component
public class Transaction implements TransactionContract {

    private final WalletContract walletService;
    private final LedgerContract ledgerService;
    private final UserService user;

    public Transaction(WalletContract walletContract, LedgerContract ledgerContract, UserService user) {
        this.walletService = walletContract;
        this.ledgerService = ledgerContract;
        this.user = user;
    }

    @Override
    public void processTransaction(TransactionDTO dto) {
        // Get authenticated user ID from JWT token
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long senderUserId = Long.parseLong(auth.getName());

        // Fetch sender (authenticated user)
        UserDataBase sender = user.buscarPorId(senderUserId).orElseThrow(() ->
                new LedgerExceptions.LedgerNotFoundException("Sender (authenticated user) not found"));

        // Determine sender wallet based on DTO sender field
        WalletEntity senderWallet = resolveSenderWallet(sender, dto.getSender());

        // Determine receiver wallet based on receiver identifier
        WalletEntity receiverWallet = resolveReceiverWallet(dto.getReceiver());

        // Verify sender has sufficient balance
        LedgerEntity senderLedger = ledgerService.findByWalletId(senderWallet.getId());
        if (senderLedger == null || senderLedger.getBalance().compareTo(dto.getAmount()) < 0) {
            throw new LedgerExceptions.LedgerNotFoundException("Insufficient balance in sender wallet");
        }

        // Credit receiver
        ledgerService.updateBalance(receiverWallet.getId(), dto.getAmount(), "Credit transaction");

        // Debit sender
        BigDecimal debitAmount = dto.getAmount().negate();
        ledgerService.updateBalance(
                senderWallet.getId(),
                debitAmount,
                dto.getContext() != null ? dto.getContext() : "Debit transaction"
        );
    }

    /**
     * Resolve sender wallet from authenticated user + optional sender identifier
     * Se sender for null/vazio, usa a primeira carteira do user
     * Se for numeric ID, busca carteira por ID do user
     * Se for nome, busca carteira por nome
     * Se for hash/address, busca carteira por address
     */
    private WalletEntity resolveSenderWallet(UserDataBase sender, String senderIdentifier) {
        List<WalletEntity> senderWallets = walletService.findByUserId(sender.getId());
        if (senderWallets == null || senderWallets.isEmpty()) {
            throw new LedgerExceptions.LedgerNotFoundException("Sender wallet not found");
        }

        // Se sender identifier não foi fornecido, usa primeira carteira
        if (senderIdentifier == null || senderIdentifier.trim().isEmpty()) {
            return senderWallets.get(0);
        }

        // Se for numeric (ID), busca por ID
        if (TransactionDTO.isNumericId(senderIdentifier)) {
            long walletId = Long.parseLong(senderIdentifier);
            return senderWallets.stream()
                    .filter(w -> w.getId() == walletId)
                    .findFirst()
                    .orElseThrow(() -> new LedgerExceptions.LedgerNotFoundException(
                            "Sender wallet with ID " + walletId + " not found"));
        }

        // Se for Bitcoin Address (hash/endereço), busca por address
        if (TransactionDTO.isBitcoinAddress(senderIdentifier) || isHashFormat(senderIdentifier)) {
            WalletEntity walletByAddress = walletService.findByAddress(senderIdentifier);
            if (walletByAddress != null && walletByAddress.getUser().getId().equals(sender.getId())) {
                return walletByAddress;
            }
            // Se não encontrou por address exato, tenta buscar entre wallets do user
            return senderWallets.stream()
                    .filter(w -> w.getAddress().equals(senderIdentifier))
                    .findFirst()
                    .orElseThrow(() -> new LedgerExceptions.LedgerNotFoundException(
                            "Sender wallet with address '" + senderIdentifier + "' not found"));
        }

        // Se for nome de carteira, busca por nome
        String senderIdentifierUpperCase = senderIdentifier != null ? senderIdentifier.toUpperCase() : null;
        return senderWallets.stream()
                .filter(w -> w.getName().equals(senderIdentifierUpperCase))
                .findFirst()
                .orElseThrow(() -> new LedgerExceptions.LedgerNotFoundException(
                        "Sender wallet with name '" + senderIdentifier + "' not found"));
    }

    /**
     * Resolve receiver wallet from multiple possible formats:
     * - Username: busca user por username, depois primeira carteira
     * - Wallet ID: busca carteira por ID
     * - Bitcoin Address/Hash: busca carteira por endereço
     */
    private WalletEntity resolveReceiverWallet(String receiverIdentifier) {
        if (receiverIdentifier == null || receiverIdentifier.trim().isEmpty()) {
            throw new LedgerExceptions.LedgerNotFoundException("Receiver identifier cannot be empty");
        }

        // Caso 1: Numeric ID (wallet ID)
        if (TransactionDTO.isNumericId(receiverIdentifier)) {
            // Tenta buscar por ID primeiro
            try {
                long walletId = Long.parseLong(receiverIdentifier);
                // Aqui seria ideal usar um findById direto, mas vamos buscar por name temporariamente
                WalletEntity wallet = walletService.findByName(receiverIdentifier);
                if (wallet != null) {
                    return wallet;
                }
            } catch (Exception e) {
                // Continue to next check
            }
        }

        // Caso 2: Bitcoin Address ou Hash (formato Base64)
        if (TransactionDTO.isBitcoinAddress(receiverIdentifier) || isHashFormat(receiverIdentifier)) {
            WalletEntity wallet = walletService.findByAddress(receiverIdentifier);
            if (wallet != null) {
                return wallet;
            }
            throw new LedgerExceptions.LedgerNotFoundException("Receiver wallet with address '" + receiverIdentifier + "' not found");
        }

        // Caso 3: Username
        UserDataBase receiver = user.findByUsername(receiverIdentifier);
        if (receiver == null) {
            throw new LedgerExceptions.LedgerNotFoundException("Receiver username '" + receiverIdentifier + "' not found");
        }

        List<WalletEntity> receiverWallets = walletService.findByUserId(receiver.getId());
        if (receiverWallets == null || receiverWallets.isEmpty()) {
            throw new LedgerExceptions.LedgerNotFoundException("Receiver wallet not found for user '" + receiverIdentifier + "'");
        }

        return receiverWallets.get(0);
    }

    /**
     * Verifica se a string parece ser um hash (Base64)
     * Padrão Base64: caracteres alfanuméricos + /+=
     */
    private boolean isHashFormat(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return false;
        }
        // Base64 típico: contém /+ e termina com = para padding
        return identifier.matches("^[A-Za-z0-9+/]+=*$") && identifier.contains("=");
    }
}
