package source.ledger.application.transaction.handler;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import source.auth.application.service.identityaccess.TransactionalAuthenticationPort;
import source.auth.application.service.identityaccess.TransactionalAuthenticationRequest;
import source.ledger.application.transaction.TransactionContext;
import source.ledger.application.transaction.TransactionHandler;
import source.ledger.application.transaction.TransactionHandlerChain;

@Component
@Order(30)
public class TransactionAuthenticationHandler implements TransactionHandler {

    private final TransactionalAuthenticationPort authenticationPort;

    public TransactionAuthenticationHandler(TransactionalAuthenticationPort authenticationPort) {
        this.authenticationPort = authenticationPort;
    }

    @Override
    public void handle(TransactionContext context, TransactionHandlerChain chain) {
        authenticationPort.authorize(TransactionalAuthenticationRequest.ledgerTransfer(
                context.getSender(),
                context.getTransaction().getTotpCode(),
                context.getTransaction().getPasskeyAssertionJson(),
                context.getTransaction().getConfirmationPassphrase()));
        chain.next(context);
    }
}
