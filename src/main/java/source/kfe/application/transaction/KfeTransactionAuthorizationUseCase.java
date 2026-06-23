package source.kfe.application.transaction;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import source.auth.AuthExceptions;
import source.auth.application.service.account.AppPinService;
import source.auth.application.service.identityaccess.TransactionalAuthenticationPort;
import source.auth.application.service.identityaccess.TransactionalAuthenticationRequest;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.UserDataBase;
import source.common.exception.ErrorCodes;
import source.kfe.dto.KfeSubmitTransactionRequest;
import source.kfe.model.KfeDirection;
import source.kfe.model.KfeRail;

import java.util.Map;

@Service
public class KfeTransactionAuthorizationUseCase {

    private final UserServiceContract userService;
    private final TransactionalAuthenticationPort transactionalAuthPort;
    private final AppPinService appPinService;

    public KfeTransactionAuthorizationUseCase(
            UserServiceContract userService,
            TransactionalAuthenticationPort transactionalAuthPort,
            AppPinService appPinService) {
        this.userService = userService;
        this.transactionalAuthPort = transactionalAuthPort;
        this.appPinService = appPinService;
    }

    public void authorize(Long userId, KfeSubmitTransactionRequest request, String deviceHash) {
        if (!requiresTransactionalAuthorization(request)) {
            return;
        }

        if (requiresAppPinAndPasskey(request)) {
            requireAppPin(request);
            UserDataBase user = authenticatedUser(userId);
            appPinService.verify(user, deviceHash, request.appPin());
            transactionalAuthPort.authorize(TransactionalAuthenticationRequest.kfeCustodialTransfer(
                    user,
                    request.passkeyAssertionJson()));
            return;
        }

        UserDataBase user = authenticatedUser(userId);
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

    private boolean requiresAppPinAndPasskey(KfeSubmitTransactionRequest request) {
        return isInternalTransfer(request)
                || (request.rail() == KfeRail.ONCHAIN && request.direction() == KfeDirection.OUTBOUND);
    }

    private boolean isInternalTransfer(KfeSubmitTransactionRequest request) {
        return request.direction() == KfeDirection.INTERNAL || request.rail() == KfeRail.INTERNAL;
    }

    private UserDataBase authenticatedUser(Long userId) {
        return userService.buscarPorId(userId)
                .orElseThrow(() -> new AuthExceptions.StructuredAuthException(
                        "Usuário autenticado não encontrado. Faça login novamente.",
                        HttpStatus.UNAUTHORIZED,
                        ErrorCodes.AUTH_INVALID_CREDENTIALS,
                        null));
    }

    private void requireAppPin(KfeSubmitTransactionRequest request) {
        if (!hasText(request.appPin())) {
            throw new AuthExceptions.StructuredAuthException(
                    "PIN do aplicativo obrigatorio para transacoes internas KFE e onchain custodial.",
                    HttpStatus.UNAUTHORIZED,
                    ErrorCodes.AUTH_TRANSACTIONAL_AUTH_REQUIRED,
                    Map.of("requiredAllOf", new String[] {"appPin", "passkeyAssertionJson"},
                            "missing", new String[] {"appPin"}));
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
