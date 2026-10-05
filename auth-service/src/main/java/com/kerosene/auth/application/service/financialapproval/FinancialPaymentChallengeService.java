package com.kerosene.auth.application.service.financialapproval;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kerosene.auth.application.service.cache.contracts.RedisServicer;
import com.kerosene.common.financial.approval.FinancialPaymentApprovalV1;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import static com.kerosene.common.financial.approval.FinancialPaymentApprovalV1.*;

/** Dedicated, single-use namespace. Cache failures never become approval. */
@Service
public class FinancialPaymentChallengeService {
    private static final String PREFIX = "financial_payment_challenge_v1:";
    private final RedisServicer redis;
    private final ObjectMapper json;
    private final SecureRandom random = new SecureRandom();
    private final String serviceId;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public FinancialPaymentChallengeService(RedisServicer redis, ObjectMapper mapper,
            @Value("${device-key.onion-service-id:${webauthn.relying-party-id:kerosene-device}}") String serviceId) {
        this(redis, mapper, serviceId, Clock.systemUTC());
    }
    public FinancialPaymentChallengeService(RedisServicer redis, ObjectMapper mapper, String serviceId, Clock clock) {
        this.redis = redis; this.serviceId = serviceId; this.clock = clock;
        this.json = mapper.copy().setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS);
    }

    public Challenge issue(Context context, String username) {
        // Bound outstanding allocations independently of random challenge IDs.
        Long count = redis.incrementWithExpire(PREFIX + "rate:" + context.userId(), 60);
        if (count == null || count > 30) { throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Payment challenge rate limit"); }
        byte[] nonce = new byte[32]; random.nextBytes(nonce); long now = clock.instant().getEpochSecond();
        var challenge = new Challenge(1, PURPOSE, UUID.randomUUID().toString(), HexFormat.of().formatHex(nonce),
                bindingHash(context), username.trim().toLowerCase(Locale.ROOT), serviceId, now, now + 90,
                "Ed25519", CANONICALIZATION);
        try { redis.setValue(PREFIX + challenge.challengeId(), json.writeValueAsString(new Stored(context, challenge)), 90); }
        catch (Exception failure) { throw new IllegalStateException("Payment challenge storage unavailable"); }
        return challenge;
    }

    public Challenge consume(Context context, Proof proof, String username) {
        final String challengeId;
        try {
            var payload = json.readTree(proof.signedPayload());
            if (!payload.isObject() || !payload.path("challengeId").isTextual()) { throw new IllegalArgumentException(); }
            challengeId = payload.path("challengeId").textValue();
            if (challengeId.length() > 128 || !challengeId.matches("[a-zA-Z0-9-]+")) { throw new IllegalArgumentException(); }
        } catch (Exception failure) { throw rejected(); }
        String raw = redis.getAndDeleteValue(PREFIX + challengeId);
        if (raw == null || raw.isBlank()) { throw rejected(); }
        final Stored stored;
        try { stored = json.readValue(raw, Stored.class); }
        catch (Exception failure) { throw rejected(); }
        var challenge = stored.challenge(); long now = clock.instant().getEpochSecond();
        if (challenge == null || !context.equals(stored.context()) || !bindingHash(context).equals(challenge.bindingHash())
                || !challengeId.equals(challenge.challengeId()) || !serviceId.equals(challenge.onionServiceId())
                || !username.trim().toLowerCase(Locale.ROOT).equals(challenge.username())
                || now < challenge.issuedAtEpochSeconds() || now >= challenge.expiresAtEpochSeconds()) { throw rejected(); }
        return challenge;
    }

    public record Stored(Context context, Challenge challenge) {
        @Override public String toString() { return "StoredFinancialChallenge[REDACTED]"; }
    }
    private static ResponseStatusException rejected() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Payment challenge expired, used or mismatched");
    }
}
