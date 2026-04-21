package source.ledger.application.transaction.handler;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import source.ledger.application.transaction.TransactionContext;
import source.ledger.application.transaction.TransactionHandler;
import source.ledger.application.transaction.TransactionHandlerChain;
import source.ledger.application.transaction.TransactionParticipantResolver;

@Component
@Order(50)
public class TransactionWalletResolutionHandler implements TransactionHandler {

    private final TransactionParticipantResolver participantResolver;

    public TransactionWalletResolutionHandler(TransactionParticipantResolver participantResolver) {
        this.participantResolver = participantResolver;
    }

    @Override
    public void handle(TransactionContext context, TransactionHandlerChain chain) {
        context.setSenderWallet(
                participantResolver.resolveSenderWallet(context.getSender(), context.getTransaction().getSender()));
        context.setReceiverWallet(
                participantResolver.resolveReceiverWallet(context.getTransaction().getReceiver()));
        chain.next(context);
    }
}
