package source.transactions.infra.externalpayments;

import org.springframework.stereotype.Component;
import source.auth.application.service.identityaccess.TransactionalAuthenticationPort;
import source.auth.application.service.identityaccess.TransactionalAuthenticationRequest;
import source.auth.application.service.identityaccess.TransactionalAuthenticationResult;
import source.transactions.application.externalpayments.ExternalPaymentsAuthorizationPort;
import source.wallet.model.WalletEntity;

@Component
public class ExternalPaymentsAuthorizationAdapter implements ExternalPaymentsAuthorizationPort {

    private final TransactionalAuthenticationPort transactionalAuthenticationPort;

    public ExternalPaymentsAuthorizationAdapter(TransactionalAuthenticationPort transactionalAuthenticationPort) {
        this.transactionalAuthenticationPort = transactionalAuthenticationPort;
    }

    @Override
    public AuthorizationResult authorizeOutboundTransfer(
            Long userId,
            WalletEntity wallet,
            String totpCode,
            String passkeyAssertionResponseJSON,
            String confirmationPassphrase) {
        if (wallet == null) {
            throw new IllegalArgumentException("Wallet does not belong to the authenticated user.");
        }
        TransactionalAuthenticationResult result = transactionalAuthenticationPort.authorize(
                TransactionalAuthenticationRequest.walletOutbound(
                        userId,
                        wallet.getUser() != null ? wallet.getUser().getId() : null,
                        wallet.getTotpSecret(),
                        totpCode,
                        passkeyAssertionResponseJSON,
                        confirmationPassphrase));
        return new AuthorizationResult(result.platformSignature());
    }
}
