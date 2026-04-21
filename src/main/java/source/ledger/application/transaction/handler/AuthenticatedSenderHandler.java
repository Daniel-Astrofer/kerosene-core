package source.ledger.application.transaction.handler;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import source.auth.model.entity.UserDataBase;
import source.ledger.application.transaction.AuthenticatedUserPort;
import source.ledger.application.transaction.TransactionContext;
import source.ledger.application.transaction.TransactionHandler;
import source.ledger.application.transaction.TransactionHandlerChain;
import source.ledger.application.transaction.TransactionParticipantResolver;

@Component
@Order(20)
public class AuthenticatedSenderHandler implements TransactionHandler {

    private final AuthenticatedUserPort authenticatedUserPort;
    private final TransactionParticipantResolver participantResolver;

    public AuthenticatedSenderHandler(
            AuthenticatedUserPort authenticatedUserPort,
            TransactionParticipantResolver participantResolver) {
        this.authenticatedUserPort = authenticatedUserPort;
        this.participantResolver = participantResolver;
    }

    @Override
    public void handle(TransactionContext context, TransactionHandlerChain chain) {
        Long senderUserId = authenticatedUserPort.getAuthenticatedUserId();
        UserDataBase sender = participantResolver.resolveAuthenticatedSender(senderUserId);
        context.setSenderUserId(senderUserId);
        context.setSender(sender);
        chain.next(context);
    }
}
