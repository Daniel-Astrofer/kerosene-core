package source.ledger.application.transaction;

import org.springframework.stereotype.Service;
import source.common.validation.FinancialAmountValidator;
import source.ledger.service.LedgerContract;

import java.math.BigDecimal;

@Service
public class TransactionLedgerService {

    private final LedgerContract ledgerService;

    public TransactionLedgerService(LedgerContract ledgerService) {
        this.ledgerService = ledgerService;
    }

    public void executeInternalTransfer(TransactionContext context) {
        FinancialAmountValidator.requirePositiveBtc(context.getTransaction().getAmount(), "amount");
        String effectiveContext = buildContext(context);
        context.setEffectiveContext(effectiveContext);

        ledgerService.updateBalance(
                context.getReceiverWallet().getId(),
                context.getTransaction().getAmount(),
                effectiveContext);

        BigDecimal debitAmount = context.getTransaction().getAmount().negate();
        ledgerService.updateBalance(
                context.getSenderWallet().getId(),
                debitAmount,
                effectiveContext);
    }

    private String buildContext(TransactionContext context) {
        String providedContext = context.getTransaction().getContext();
        if (providedContext != null && !providedContext.trim().isEmpty()) {
            return providedContext;
        }

        return String.format(
                "Transfer from @%s to @%s",
                context.getSender().getUsername(),
                context.getReceiverWallet().getUser().getUsername());
    }
}
