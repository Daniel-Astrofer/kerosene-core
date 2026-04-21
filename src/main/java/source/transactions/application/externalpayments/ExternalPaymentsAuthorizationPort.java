package source.transactions.application.externalpayments;

import source.wallet.model.WalletEntity;

public interface ExternalPaymentsAuthorizationPort {

    AuthorizationResult authorizeOutboundTransfer(
            Long userId,
            WalletEntity wallet,
            String totpCode,
            String passkeyAssertionResponseJSON,
            String confirmationPassphrase);

    record AuthorizationResult(String platformSignature) {
    }
}
