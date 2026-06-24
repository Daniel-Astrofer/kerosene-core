package source.auth.integration;

import org.springframework.stereotype.Service;
import source.auth.AuthExceptions;
import source.auth.application.service.account.AppPinService;
import source.auth.application.service.identityaccess.TransactionalAuthenticationPort;
import source.auth.application.service.identityaccess.TransactionalAuthenticationRequest;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.UserDataBase;
import source.common.financial.FinancialTransactionApprovalPort;

@Service
public class AuthFinancialTransactionApprovalAdapter implements FinancialTransactionApprovalPort {

    private final UserServiceContract userService;
    private final AppPinService localFactorService;
    private final TransactionalAuthenticationPort transactionAuth;

    public AuthFinancialTransactionApprovalAdapter(
            UserServiceContract userService,
            AppPinService localFactorService,
            TransactionalAuthenticationPort transactionAuth) {
        this.userService = userService;
        this.localFactorService = localFactorService;
        this.transactionAuth = transactionAuth;
    }

    @Override
    public void approveLocalFactor(Long userId, String deviceRef, String factor) {
        localFactorService.verify(authenticatedUser(userId), deviceRef, factor);
    }

    @Override
    public void approveCustodyTransfer(Long userId, String assertion) {
        transactionAuth.authorize(TransactionalAuthenticationRequest.kfeCustodialTransfer(
                authenticatedUser(userId),
                assertion));
    }

    @Override
    public void approveWalletOutbound(
            Long actorUserId,
            Long ownerUserId,
            String factorA,
            String factorB,
            String factorC) {
        transactionAuth.authorize(TransactionalAuthenticationRequest.walletOutbound(
                actorUserId,
                ownerUserId,
                null,
                factorA,
                factorB,
                factorC));
    }

    @Override
    public void approveColdWalletPsbt(Long userId, String factor) {
        transactionAuth.authorize(TransactionalAuthenticationRequest.kfeColdWalletPsbt(
                authenticatedUser(userId),
                factor));
    }

    private UserDataBase authenticatedUser(Long userId) {
        return userService.buscarPorId(userId)
                .orElseThrow(() -> new AuthExceptions.InvalidCredentials(
                        "Usuário autenticado não encontrado. Faça login novamente."));
    }
}
