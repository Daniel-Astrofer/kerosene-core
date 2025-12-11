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

@Component
public class Transaction implements TransactionContract{

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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = Long.parseLong(auth.getName());


        UserDataBase receiver = user.findByUsername(dto.getReceiver());
        if (receiver == null) {
            throw new LedgerExceptions.LedgerNotFoundException("Receiver not found");
        }
        WalletEntity receiverWallet = walletService.findByName(receiver.getUsername());
        UserDataBase sender = user.findByUsername(dto.getSender());
        LedgerEntity receiverLedger = ledgerService.updateBalance(receiverWallet.getId(),dto.getAmount(), "Credit transaction");

        BigDecimal debitAmount = dto.getAmount().negate();

        ledgerService.updateBalance(
                sender.getId(),
                debitAmount,
                dto.getContext() != null ? dto.getContext() : "Debit transaction"
        );

    }



}
