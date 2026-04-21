package source.ledger.infra.balance;

import org.springframework.stereotype.Component;
import source.ledger.application.balance.LedgerBalanceUpdate;
import source.ledger.application.balance.LedgerBalanceUpdatePort;
import source.ledger.event.BalanceEventPublisher;

@Component
public class WebSocketLedgerBalanceUpdateAdapter implements LedgerBalanceUpdatePort {

    private final BalanceEventPublisher balanceEventPublisher;

    public WebSocketLedgerBalanceUpdateAdapter(BalanceEventPublisher balanceEventPublisher) {
        this.balanceEventPublisher = balanceEventPublisher;
    }

    @Override
    public void publishBalanceUpdated(LedgerBalanceUpdate update) {
        balanceEventPublisher.publishBalanceUpdate(
                update.walletId(),
                update.walletName(),
                update.userId(),
                update.newBalance(),
                update.amount(),
                update.context());
    }
}
