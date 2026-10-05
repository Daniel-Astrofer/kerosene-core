package com.kerosene.auth.application.infra.kfe;

import org.springframework.stereotype.Service;
import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.application.service.account.AppPinService;
import com.kerosene.auth.application.service.identityaccess.TransactionalAuthenticationPort;
import com.kerosene.auth.application.service.identityaccess.TransactionalAuthenticationRequest;
import com.kerosene.auth.application.service.user.contract.UserServiceContract;
import com.kerosene.auth.model.entity.UserDataBase;
import com.kerosene.common.financial.approval.DeviceProof;
import com.kerosene.common.financial.approval.FinancialTransactionApprovalPort;
import com.kerosene.common.financial.approval.PasskeyAssertion;
import com.kerosene.common.financial.approval.RecoveryApproval;

/**
 * Adapts KFE financial approval checks to the authentication service's local factors and
 * transactional authorization policy. User resolution is anchored to persisted auth identities;
 * approval never trusts a caller-supplied username in place of a user ID.
 */
@Service
public class AuthFinancialTransactionApprovalAdapter implements FinancialTransactionApprovalPort {

    /** Loads the persisted authenticated user corresponding to a numeric identity. */
    private final UserServiceContract userService;
    /** Verifies a user's local application PIN/device factor. */
    private final AppPinService localFactorService;
    /** Authorizes transactional assertions and multi-factor requests. */
    private final TransactionalAuthenticationPort transactionAuth;

    /**
     * Creates the adapter with account lookup, local-factor verification, and transactional authorization.
     *
     * @param userService source of persisted authentication users
     * @param localFactorService verifier for local device/application factors
     * @param transactionAuth policy port for high-risk transaction approval
     */
    public AuthFinancialTransactionApprovalAdapter(
            UserServiceContract userService,
            AppPinService localFactorService,
            TransactionalAuthenticationPort transactionAuth) {
        this.userService = userService;
        this.localFactorService = localFactorService;
        this.transactionAuth = transactionAuth;
    }

    /** Verifies the submitted local factor for the resolved user and device reference. */
    @Override
    public void approveLocalFactor(Long userId, String deviceRef, DeviceProof factor) {
        localFactorService.verify(authenticatedUser(userId), deviceRef, factor != null ? factor.proof() : null);
    }

    /**
     * Authorizes a custodial transfer using the KFE-specific assertion policy.
     *
     * @param userId authenticated user identity
     * @param assertion passkey assertion consumed by the auth policy
     */
    @Override
    public void approveCustodyTransfer(Long userId, PasskeyAssertion assertion) {
        transactionAuth.authorize(TransactionalAuthenticationRequest.kfeCustodialTransfer(
                authenticatedUser(userId),
                assertion != null ? assertion.signature() : null));
    }

    /**
     * Authorizes an outbound wallet operation against the acting user, owner, and supplied factors.
     *
     * @param actorUserId user initiating the operation
     * @param ownerUserId user who owns the wallet
     * @param passkeyAssertion passkey proof
     * @param recoveryApproval recovery approval proof
     * @param deviceProof device proof
     */
    @Override
    public void approveWalletOutbound(
            Long actorUserId,
            Long ownerUserId,
            PasskeyAssertion passkeyAssertion,
            RecoveryApproval recoveryApproval,
            DeviceProof deviceProof) {
        transactionAuth.authorize(TransactionalAuthenticationRequest.walletOutbound(
                actorUserId,
                ownerUserId,
                null,
                recoveryApproval != null ? recoveryApproval.proof() : null,
                passkeyAssertion != null ? passkeyAssertion.clientDataJson() : null,
                deviceProof != null ? deviceProof.proof() : null));
    }

    /**
     * Authorizes a cold-wallet PSBT operation through the transactional authentication policy.
     *
     * @param userId authenticated user identity
     * @param factor device proof bound to the PSBT challenge
     */
    @Override
    public void approveColdWalletPsbt(Long userId, DeviceProof factor) {
        transactionAuth.authorize(TransactionalAuthenticationRequest.kfeColdWalletPsbt(
                authenticatedUser(userId),
                factor != null ? factor.proof() : null));
    }

    /**
     * Resolves the persisted user or rejects approval as invalid credentials.
     *
     * @param userId database user identifier
     * @return persisted user bound to the approval operation
     * @throws AuthExceptions.InvalidCredentials if no persisted user exists for the identifier
     */
    private UserDataBase authenticatedUser(Long userId) {
        return userService.buscarPorId(userId)
                .orElseThrow(() -> new AuthExceptions.InvalidCredentials(
                        "Usuário autenticado não encontrado. Faça login novamente."));
    }
}
