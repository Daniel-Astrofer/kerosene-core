package com.kerosene.auth.application.service.financialapproval;

import com.kerosene.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import com.kerosene.auth.application.service.devicebinding.DeviceCredentialReplayGuard;
import com.kerosene.auth.model.entity.DeviceKeyCredential;
import com.kerosene.auth.model.entity.UserDataBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import static com.kerosene.common.financial.approval.FinancialPaymentApprovalV1.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialPaymentCredentialVerifierTest {
    static Context context() { return new Context(7, "device-ref", "key", "ONCHAIN", "OUTBOUND", "wallet", null, 1000, 10, "address", "memo", null, 2L, 3, null); }
    static UserDataBase user() { var user = new UserDataBase(); ReflectionTestUtils.setField(user, "id", 7L); user.setUsername("alice"); return user; }
    static Challenge challenge() {
        long now = Instant.now().getEpochSecond();
        return new Challenge(1, PURPOSE, "challenge-id", "a".repeat(64), bindingHash(context()), "alice", "service", now - 2, now + 88, "Ed25519", CANONICALIZATION);
    }

    @Test void realEd25519SignatureAdvancesScopedCounterOnlyAfterVerification() throws Exception {
        var f = new Fixture(); var proof = f.sign(signedPayload(f.challenge, "cred", "install", 2, f.time));
        f.verifier.verify(context(), f.challenge, proof);
        verify(f.repository).advanceCounter(eq("cred"), eq(7L), eq(2L), any());
        verify(f.guard).clearFailures(7L, DeviceCredentialReplayGuard.credentialRefFromString("cred"));
    }

    @ParameterizedTest @ValueSource(strings = {"signature", "payload", "generic", "counter", "fraction", "duplicate", "owner", "revoked", "install", "service", "locked", "binding"})
    void invalidProofsAndCredentialBindingsCannotAdvanceCounter(String fault) throws Exception {
        var f = new Fixture(); String payload = signedPayload(f.challenge, "cred", "install", 2, f.time);
        switch (fault) {
            case "payload" -> payload += " ";
            case "generic" -> payload = payload.replace(PURPOSE, "AUTH_DEVICE_KEY");
            case "counter" -> f.credential.setCounter(2);
            case "fraction" -> payload = payload.replace("\"counter\":2", "\"counter\":2.1");
            case "duplicate" -> payload = payload.replace("\"counter\":2", "\"counter\":2,\"counter\":2");
            case "owner" -> ReflectionTestUtils.setField(f.credential.getUser(), "id", 8L);
            case "revoked" -> f.credential.setStatus("REVOKED");
            case "install" -> f.credential.setDeviceInstallId("other");
            case "service" -> f.credential.setOnionServiceId("other");
            case "locked" -> when(f.guard.isLocked(anyLong(), any())).thenReturn(true);
            case "binding" -> payload = payload.replace(f.challenge.bindingHash(), "b".repeat(64));
        }
        var original = f.sign(payload);
        var proof = fault.equals("signature") ? new Proof(1, PROOF_TYPE, "cred", "install", payload, "A".repeat(86)) : original;
        assertThatThrownBy(() -> f.verifier.verify(context(), f.challenge, proof)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(f.repository, never()).advanceCounter(any(), any(), anyLong(), any());
    }

    @Test void failedCounterCompareAndSwapIsNotApproval() throws Exception {
        var f = new Fixture(); when(f.repository.advanceCounter(any(), any(), anyLong(), any())).thenReturn(0);
        var proof = f.sign(signedPayload(f.challenge, "cred", "install", 2, f.time));
        assertThatThrownBy(() -> f.verifier.verify(context(), f.challenge, proof)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(f.guard).recordReplayFailure(eq(7L), any(), eq(PROOF_TYPE));
        verify(f.guard, never()).clearFailures(anyLong(), any());
    }

    static class Fixture {
        final DeviceKeyCredentialRepository repository = mock(DeviceKeyCredentialRepository.class);
        final DeviceCredentialReplayGuard guard = mock(DeviceCredentialReplayGuard.class);
        final FinancialPaymentCredentialVerifier verifier = new FinancialPaymentCredentialVerifier(repository, guard, new com.fasterxml.jackson.databind.ObjectMapper());
        final DeviceKeyCredential credential = new DeviceKeyCredential();
        final Challenge challenge = challenge(); final long time = Instant.now().getEpochSecond();
        final java.security.KeyPair keys;
        Fixture() throws Exception {
            keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); byte[] encoded = keys.getPublic().getEncoded();
            credential.setUser(user()); credential.setCredentialId("cred"); credential.setDeviceInstallId("install");
            credential.setOnionServiceId("service"); credential.setCounter(1);
            credential.setPublicKeyEd25519(Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOfRange(encoded, encoded.length-32, encoded.length)));
            when(repository.findByCredentialIdAndUserId("cred", 7L)).thenReturn(Optional.of(credential));
            when(repository.advanceCounter(any(), any(), anyLong(), any())).thenReturn(1);
        }
        Proof sign(String payload) throws Exception {
            var signer = Signature.getInstance("Ed25519"); signer.initSign(keys.getPrivate()); signer.update(payload.getBytes(StandardCharsets.UTF_8));
            return new Proof(1, PROOF_TYPE, "cred", "install", payload, Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
        }
    }

    @Test void realLocalTransactionCommitsCounterAndSqlRejectsConcurrentStaleViewOrRevocation() throws Exception {
        // JDBC adapter models the repository CAS with H2; this does not validate the full Auth JPA schema.
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:credential_" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var sql = new org.springframework.jdbc.core.JdbcTemplate(source);
        sql.execute("create table credential (id varchar primary key, owner bigint, counter bigint, status varchar)");
        sql.update("insert into credential values ('cred',7,1,'ACTIVE')");
        var tx = new org.springframework.jdbc.datasource.DataSourceTransactionManager(source);
        var f = new Fixture();
        when(f.repository.advanceCounter(any(), any(), anyLong(), any())).thenAnswer(i -> sql.update(
                "update credential set counter=? where id=? and owner=? and status='ACTIVE' and counter<?",
                i.getArgument(2), i.getArgument(0), i.getArgument(1), i.getArgument(2)));
        var proxy = new org.springframework.aop.framework.ProxyFactory(f.verifier); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(tx,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var verifier = (FinancialPaymentCredentialVerifier) proxy.getProxy();
        var proof = f.sign(signedPayload(f.challenge, "cred", "install", 2, f.time));
        verifier.verify(context(), f.challenge, proof);
        assertThat(sql.queryForObject("select counter from credential", Long.class)).isEqualTo(2L);
        // Same stale projection still has counter=1, but SQL refuses another use of counter=2.
        assertThatThrownBy(() -> verifier.verify(context(), f.challenge, proof)).isInstanceOf(RuntimeException.class);
        sql.update("update credential set status='REVOKED'");
        var next = f.sign(signedPayload(f.challenge, "cred", "install", 3, f.time));
        assertThatThrownBy(() -> verifier.verify(context(), f.challenge, next)).isInstanceOf(RuntimeException.class);
        assertThat(sql.queryForObject("select counter from credential", Long.class)).isEqualTo(2L);
        sql.execute("shutdown");
    }
}
