package com.kerosene.auth.application.service.financialapproval;

import com.kerosene.auth.application.service.account.AppPinService;
import com.kerosene.auth.application.service.user.contract.UserServiceContract;
import com.kerosene.auth.model.enums.AccountSecurityType;
import com.kerosene.common.financial.approval.FinancialPaymentApprovalPort;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Objects;
import static com.kerosene.common.financial.approval.FinancialPaymentApprovalV1.*;

/** No encompassing transaction: PIN attempts and credential counter each own their local commit. */
@Service
public class FinancialPaymentApprovalService implements FinancialPaymentApprovalPort {
    private final UserServiceContract users;
    private final AppPinService pins;
    private final FinancialPaymentChallengeService challenges;
    private final FinancialPaymentCredentialVerifier verifier;
    public FinancialPaymentApprovalService(UserServiceContract users, AppPinService pins,
            FinancialPaymentChallengeService challenges, FinancialPaymentCredentialVerifier verifier) {
        this.users = users; this.pins = pins; this.challenges = challenges; this.verifier = verifier;
    }

    @Override public Response approve(Request request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) { throw new IllegalStateException("Payment approval cannot join an existing transaction"); }
        Objects.requireNonNull(request); var context = request.context();
        var user = users.buscarPorId(context.userId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user unavailable"));
        if (!Objects.equals(user.getId(), context.userId())) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user unavailable"); }
        var security = user.getAccountSecurity() == null ? AccountSecurityType.STANDARD : user.getAccountSecurity();
        if (!context.requiresPin() && security != AccountSecurityType.STANDARD && security != AccountSecurityType.PASSKEY) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Advanced payment approval is not supported by protocol v1");
        }
        // Never reinterpret PIN/TOTP/passphrase as device proofs. Unsupported supplied factors are not approvals.
        if (context.requiresPin()) { pins.verify(user, context.deviceRef(), request.appPin()); }
        if (request.proof() == null) {
            var challenge = challenges.issue(context, user.getUsername());
            return new Response(1, "CHALLENGE", bindingHash(context), null, challenge.expiresAtEpochSeconds(), challenge);
        }
        var challenge = challenges.consume(context, request.proof(), user.getUsername());
        verifier.verify(context, challenge, request.proof());
        if (Instant.now().getEpochSecond() >= challenge.expiresAtEpochSeconds()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment approval expired");
        }
        return new Response(1, "APPROVED", bindingHash(context), challenge.challengeId(), challenge.expiresAtEpochSeconds(), null);
    }
}
