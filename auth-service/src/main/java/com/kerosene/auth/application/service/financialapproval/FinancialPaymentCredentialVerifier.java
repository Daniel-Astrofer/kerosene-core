package com.kerosene.auth.application.service.financialapproval;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kerosene.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import com.kerosene.auth.application.service.devicebinding.DeviceCredentialReplayGuard;
import com.kerosene.common.financial.approval.FinancialPaymentApprovalV1;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import static com.kerosene.common.financial.approval.FinancialPaymentApprovalV1.*;

/** Real Ed25519 verification followed by owner/status-scoped counter CAS in one local transaction. */
@Service
public class FinancialPaymentCredentialVerifier {
    private final DeviceKeyCredentialRepository credentials;
    private final DeviceCredentialReplayGuard replayGuard;
    private final ObjectMapper json;
    public FinancialPaymentCredentialVerifier(DeviceKeyCredentialRepository credentials, DeviceCredentialReplayGuard replayGuard, ObjectMapper mapper) {
        this.credentials = credentials; this.replayGuard = replayGuard;
        this.json = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @Transactional
    public void verify(Context context, Challenge challenge, Proof proof) {
        var credential = credentials.findByCredentialIdAndUserId(proof.credentialId(), context.userId()).orElseThrow(FinancialPaymentCredentialVerifier::rejected);
        String ref = DeviceCredentialReplayGuard.credentialRefFromString(proof.credentialId());
        if (credential.getUser() == null || !Objects.equals(context.userId(), credential.getUser().getId())
                || !Objects.equals(credential.getCredentialId(), proof.credentialId())
                || !Objects.equals(credential.getDeviceInstallId(), proof.deviceInstallId())
                || !"ACTIVE".equals(credential.getStatus()) || credential.getRevokedAt() != null
                || !"Ed25519".equals(credential.getAlgorithm())
                || !Objects.equals(credential.getOnionServiceId(), challenge.onionServiceId())
                || replayGuard.isLocked(context.userId(), ref)) { throw rejected(); }
        final long counter;
        try {
            var payload = json.readTree(proof.signedPayload());
            var countNode = payload.path("counter"); var timeNode = payload.path("issuedAtEpochSeconds");
            if (!countNode.isIntegralNumber() || !countNode.canConvertToLong() || !timeNode.isIntegralNumber()
                    || !timeNode.canConvertToLong()) { throw new IllegalArgumentException(); }
            counter = countNode.longValue(); long time = timeNode.longValue();
            long now = Instant.now().getEpochSecond();
            if (!bindingHash(context).equals(challenge.bindingHash()) || now >= challenge.expiresAtEpochSeconds()
                    || time > now + 30 || counter <= credential.getCounter()) { throw new IllegalArgumentException(); }
            String expected = signedPayload(challenge, proof.credentialId(), proof.deviceInstallId(), counter, time);
            if (!expected.equals(proof.signedPayload()) || !proof.signature().matches("[A-Za-z0-9_-]{86}")) { throw new IllegalArgumentException(); }
            byte[] raw = Base64.getUrlDecoder().decode(credential.getPublicKeyEd25519());
            byte[] signature = Base64.getUrlDecoder().decode(proof.signature());
            if (raw.length != 32 || signature.length != 64) { throw new IllegalArgumentException(); }
            byte[] encoded = new byte[44]; byte[] prefix = HexFormat.of().parseHex("302a300506032b6570032100");
            System.arraycopy(prefix, 0, encoded, 0, prefix.length); System.arraycopy(raw, 0, encoded, prefix.length, raw.length);
            var key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
            var verifier = Signature.getInstance("Ed25519"); verifier.initVerify(key);
            verifier.update(expected.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(signature)) { throw new IllegalArgumentException(); }
        } catch (Exception failure) { throw rejected(); }
        if (credentials.advanceCounter(proof.credentialId(), context.userId(), counter, LocalDateTime.now()) != 1) {
            replayGuard.recordReplayFailure(context.userId(), ref, PROOF_TYPE); throw rejected();
        }
        replayGuard.clearFailures(context.userId(), ref);
    }
    private static ResponseStatusException rejected() { return new ResponseStatusException(HttpStatus.FORBIDDEN, "Financial device signature rejected"); }
}
