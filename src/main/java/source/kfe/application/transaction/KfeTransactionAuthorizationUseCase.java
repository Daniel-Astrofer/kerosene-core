package source.kfe.application.transaction;

import org.springframework.stereotype.Service;
import source.auth.application.service.identityaccess.TransactionalAuthenticationPort;
import source.auth.application.service.identityaccess.TransactionalAuthenticationRequest;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.UserDataBase;
import source.kfe.dto.KfeSubmitTransactionRequest;
import source.kfe.model.KfeDirection;
import source.kfe.model.KfeRail;

@Service
public class KfeTransactionAuthorizationUseCase {

    private final UserServiceContract userService;
    private final TransactionalAuthenticationPort transactionalAuthPort;

    public KfeTransactionAuthorizationUseCase(
            UserServiceContract userService,
            TransactionalAuthenticationPort transactionalAuthPort) {
        this.userService = userService;
        this.transactionalAuthPort = transactionalAuthPort;
    }

    public void authorize(Long userId, KfeSubmitTransactionRequest request) {
        if (!requiresTransactionalAuthorization(request)) {
            return;
        }
        if (request.direction() == KfeDirection.INTERNAL || request.rail() == KfeRail.INTERNAL) {
            requireTransactionalAuthorizationMaterial(request);
        }
        UserDataBase user = userService.buscarPorId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        transactionalAuthPort.authorize(TransactionalAuthenticationRequest.walletOutbound(
                userId,
                userId,
                user.getTOTPSecret(),
                request.totpCode(),
                request.passkeyAssertionJson(),
                request.confirmationPassphrase()));
    }

    private boolean requiresTransactionalAuthorization(KfeSubmitTransactionRequest request) {
        return request.direction() == KfeDirection.OUTBOUND
                || request.direction() == KfeDirection.INTERNAL
                || request.rail() == KfeRail.INTERNAL;
    }

    private void requireTransactionalAuthorizationMaterial(KfeSubmitTransactionRequest request) {
        if (!hasText(request.totpCode())
                && !hasText(request.passkeyAssertionJson())
                && !hasText(request.confirmationPassphrase())) {
            throw new SecurityException("Transactional authentication is required for KFE internal transfers.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
