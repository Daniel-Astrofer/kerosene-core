package com.kerosene.auth.application.service.financialapproval;

import com.kerosene.auth.application.service.account.AppPinService;
import com.kerosene.auth.application.service.cache.contracts.RedisServicer;
import com.kerosene.auth.application.service.user.contract.UserServiceContract;
import com.kerosene.auth.model.enums.AccountSecurityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import static com.kerosene.common.financial.approval.FinancialPaymentApprovalV1.*;
import static com.kerosene.auth.application.service.financialapproval.FinancialPaymentCredentialVerifierTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialPaymentApprovalServiceTest {
    @Test void realChallengeAndSignatureGraphApprovesOnceAfterPinAndRejectsReuse() throws Exception {
        var f = new Fixture(); var users = mock(UserServiceContract.class); var pins = mock(AppPinService.class);
        var redis = mock(RedisServicer.class); var cache = new ConcurrentHashMap<String, String>();
        when(redis.incrementWithExpire(any(), anyLong())).thenReturn(1L);
        doAnswer(i -> { cache.put(i.getArgument(0), i.getArgument(1)); return null; }).when(redis).setValue(any(), any(), anyLong());
        when(redis.getAndDeleteValue(any())).thenAnswer(i -> cache.remove(i.getArgument(0)));
        var challenges = new FinancialPaymentChallengeService(redis, new com.fasterxml.jackson.databind.ObjectMapper(), "service");
        when(users.buscarPorId(7L)).thenReturn(Optional.of(f.credential.getUser()));
        var service = new FinancialPaymentApprovalService(users, pins, challenges, f.verifier);
        var first = service.approve(new Request(1, context(), "1234", null, null, null));
        assertThat(first.status()).isEqualTo("CHALLENGE");
        verify(f.repository, never()).advanceCounter(any(), any(), anyLong(), any());
        var c = first.challenge();
        var proof = f.sign(signedPayload(c, "cred", "install", 2, c.issuedAtEpochSeconds()));
        var request = new Request(1, context(), "1234", null, null, proof);
        var approved = service.approve(request);
        assertThat(approved.status()).isEqualTo("APPROVED"); assertThat(approved.approvalId()).isEqualTo(c.challengeId());
        assertThat(approved.bindingHash()).isEqualTo(bindingHash(context()));
        assertThatThrownBy(() -> service.approve(request)).isInstanceOf(RuntimeException.class);
        verify(f.repository, times(1)).advanceCounter(any(), any(), anyLong(), any());
        verify(pins, times(3)).verify(f.credential.getUser(), "device-ref", "1234");
    }

    @Test void invalidPinOrAmbientTransactionPreventsChallengeAndSignatureWork() {
        var users = mock(UserServiceContract.class); var pins = mock(AppPinService.class);
        var challenges = mock(FinancialPaymentChallengeService.class); var verifier = mock(FinancialPaymentCredentialVerifier.class);
        var u = user(); when(users.buscarPorId(7L)).thenReturn(Optional.of(u));
        var service = new FinancialPaymentApprovalService(users, pins, challenges, verifier);
        var failure = new IllegalStateException("pin rejected"); doThrow(failure).when(pins).verify(u, "device-ref", "wrong");
        assertThatThrownBy(() -> service.approve(new Request(1, context(), "wrong", null, null, null))).isSameAs(failure);
        verifyNoInteractions(challenges, verifier);
        clearInvocations(users, pins);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThatThrownBy(() -> service.approve(null)).isInstanceOf(IllegalStateException.class); verifyNoInteractions(users, pins); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }

    @ParameterizedTest @EnumSource(AccountSecurityType.class)
    void lightningChoosesServerPolicyAndNeverDowngradesAdvancedFactors(AccountSecurityType security) {
        var users = mock(UserServiceContract.class); var pins = mock(AppPinService.class);
        var challenges = mock(FinancialPaymentChallengeService.class); var verifier = mock(FinancialPaymentCredentialVerifier.class);
        var u = user(); u.setAccountSecurity(security); when(users.buscarPorId(7L)).thenReturn(Optional.of(u));
        var x = context(); var ctx = new Context(7, x.deviceRef(), x.idempotencyKey(), "LIGHTNING", "OUTBOUND", x.sourceWalletId(), null, 1000, 10, "lnbc", "memo", null, null, null, null);
        var c = challenge(); var matching = new Challenge(1, PURPOSE, c.challengeId(), c.challenge(), bindingHash(ctx), c.username(), c.onionServiceId(), c.issuedAtEpochSeconds(), c.expiresAtEpochSeconds(), c.algorithm(), c.canonicalization());
        when(challenges.issue(ctx, "alice")).thenReturn(matching);
        var service = new FinancialPaymentApprovalService(users, pins, challenges, verifier);
        if (security == AccountSecurityType.STANDARD || security == AccountSecurityType.PASSKEY) {
            assertThat(service.approve(new Request(1, ctx, null, null, null, null)).status()).isEqualTo("CHALLENGE");
        } else {
            assertThatThrownBy(() -> service.approve(new Request(1, ctx, "1234", "000000", "phrase", null))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            verifyNoInteractions(challenges, verifier);
        }
        verifyNoInteractions(pins);
    }
}
