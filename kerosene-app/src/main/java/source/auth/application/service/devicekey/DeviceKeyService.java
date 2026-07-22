package source.auth.application.service.devicekey;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.dto.devicekey.DeviceKeyChallengeResponse;
import source.auth.dto.devicekey.DeviceKeyRegistrationRequest;
import source.auth.dto.devicekey.DeviceKeyVerifyRequest;
import source.auth.model.entity.DeviceKeyCredential;
import source.auth.model.entity.UserDataBase;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class DeviceKeyService {

    public static final String ALGORITHM = "Ed25519";
    public static final String CANONICALIZATION = "KEROSENE_JSON_V1";
    public static final String REGISTER_TYPE = "REGISTER_DEVICE_KEY";
    public static final String AUTH_TYPE = "AUTH_DEVICE_KEY";

    private static final String CHALLENGE_PREFIX = "device_key_challenge:";
    private static final byte[] ED25519_X509_PREFIX = new byte[] {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };
    private static final TypeReference<LinkedHashMap<String, Object>> PAYLOAD_TYPE =
            new TypeReference<>() {};

    private final RedisServicer redisService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String onionServiceId;
    private final long challengeTtlSeconds;

    public DeviceKeyService(
            RedisServicer redisService,
            ObjectMapper objectMapper,
            @Value("${device-key.onion-service-id:${webauthn.relying-party-id:kerosene-device}}")
            String onionServiceId,
            @Value("${device-key.challenge-ttl-seconds:90}") long challengeTtlSeconds) {
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.onionServiceId = normalizeRequired(onionServiceId, "kerosene-device");
        this.challengeTtlSeconds = challengeTtlSeconds > 0 ? challengeTtlSeconds : 90L;
    }

    public DeviceKeyChallengeResponse startRegistrationChallenge(String sessionId, String username) {
        return issueChallenge(
                DeviceKeyChallengePurpose.REGISTER_DEVICE_KEY,
                normalizeUsername(username),
                null,
                normalizeRequired(sessionId, ""));
    }

    public DeviceKeyChallengeResponse startAuthenticationChallenge(UserDataBase user) {
        return issueChallenge(
                DeviceKeyChallengePurpose.AUTH_DEVICE_KEY,
                normalizeUsername(user.getUsername()),
                user.getId(),
                null);
    }

    public DeviceKeyChallengeResponse startAuthenticatedRegistrationChallenge(UserDataBase user) {
        return issueChallenge(
                DeviceKeyChallengePurpose.REGISTER_DEVICE_KEY,
                normalizeUsername(user.getUsername()),
                user.getId(),
                "");
    }

    public VerifiedDeviceKeyRegistration verifyRegistration(
            DeviceKeyRegistrationRequest request,
            String expectedSessionId,
            String expectedUsername) {
        requireNonBlank(request.getCredentialId(), "credentialId is required.");
        requireNonBlank(request.getPublicKey(), "publicKey is required.");
        requireNonBlank(request.getDeviceInstallId(), "deviceInstallId is required.");

        byte[] publicKeyBytes = decodeBase64Flexible(request.getPublicKey(), "publicKey");
        String publicKeySha256 = base64Url(sha256(publicKeyBytes));
        if (request.getPublicKeySha256() != null && !request.getPublicKeySha256().isBlank()
                && !Objects.equals(publicKeySha256, request.getPublicKeySha256().trim())) {
            throw new DeviceKeyProtocolException("publicKeySha256 does not match publicKey.");
        }

        LinkedHashMap<String, Object> payload = parsePayload(request.getSignedPayload());
        long counter = longField(payload, "counter");
        long issuedAt = longField(payload, "issuedAtEpochSeconds");
        validateIssuedAt(issuedAt);

        DeviceKeyChallengeState challenge = consumeChallenge(stringField(payload, "challengeId"));
        if (challenge.purpose() != DeviceKeyChallengePurpose.REGISTER_DEVICE_KEY) {
            throw new DeviceKeyProtocolException("Challenge purpose does not allow device key registration.");
        }
        if (!Objects.equals(normalizeUsername(expectedUsername), normalizeUsername(challenge.username()))) {
            throw new DeviceKeyProtocolException("Challenge username mismatch.");
        }
        if (!Objects.equals(normalizeRequired(expectedSessionId, ""), normalizeRequired(challenge.sessionId(), ""))) {
            throw new DeviceKeyProtocolException("Challenge session mismatch.");
        }

        Map<String, Object> expectedPayload = registrationPayload(
                challenge,
                request.getCredentialId().trim(),
                request.getDeviceInstallId().trim(),
                publicKeySha256,
                counter,
                issuedAt);
        verifyCanonicalPayload(request.getSignedPayload(), expectedPayload);
        verifySignature(publicKeyBytes, request.getSignedPayload(), request.getSignature());

        return new VerifiedDeviceKeyRegistration(
                request.getCredentialId().trim(),
                firstNonBlank(request.getUserHandle(), request.getCredentialId()).trim(),
                request.getPublicKey().trim(),
                publicKeySha256,
                counter,
                firstNonBlank(request.getDeviceName(), "Dispositivo Kerosene"),
                request.getDeviceInstallId().trim(),
                firstNonBlank(request.getKeyStorage(), "SECURE_STORAGE"),
                firstNonBlank(request.getPlatform(), ""),
                firstNonBlank(request.getBrowser(), ""),
                firstNonBlank(request.getBrand(), ""),
                firstNonBlank(request.getModel(), ""),
                firstNonBlank(request.getSerialNumber(), ""),
                onionServiceId);
    }

    public long verifyAuthentication(
            DeviceKeyVerifyRequest request,
            UserDataBase user,
            DeviceKeyCredential credential) {
        requireNonBlank(request.getCredentialId(), "credentialId is required.");
        requireNonBlank(request.getDeviceInstallId(), "deviceInstallId is required.");
        requireNonBlank(credential.getPublicKeyEd25519(), "Stored public key is missing.");

        if (!"ACTIVE".equalsIgnoreCase(credential.getStatus())) {
            throw new DeviceKeyProtocolException("Device key is not active.");
        }
        if (!Objects.equals(request.getDeviceInstallId().trim(), credential.getDeviceInstallId())) {
            throw new DeviceKeyProtocolException("deviceInstallId does not match credential.");
        }
        if (!ALGORITHM.equals(credential.getAlgorithm())) {
            throw new DeviceKeyProtocolException("Stored algorithm is not supported.");
        }

        LinkedHashMap<String, Object> payload = parsePayload(request.getSignedPayload());
        long counter = longField(payload, "counter");
        long issuedAt = longField(payload, "issuedAtEpochSeconds");
        validateIssuedAt(issuedAt);

        DeviceKeyChallengeState challenge = consumeChallenge(stringField(payload, "challengeId"));
        if (challenge.purpose() != DeviceKeyChallengePurpose.AUTH_DEVICE_KEY) {
            throw new DeviceKeyProtocolException("Challenge purpose does not allow authentication.");
        }
        if (!Objects.equals(user.getId(), challenge.userId())) {
            throw new DeviceKeyProtocolException("Challenge user mismatch.");
        }
        if (!Objects.equals(normalizeUsername(user.getUsername()), normalizeUsername(challenge.username()))) {
            throw new DeviceKeyProtocolException("Challenge username mismatch.");
        }
        if (counter <= credential.getCounter()) {
            throw new DeviceKeyReplayException("Device key counter did not advance.");
        }

        Map<String, Object> expectedPayload = authenticationPayload(
                challenge,
                user.getUsername(),
                credential.getCredentialId(),
                credential.getDeviceInstallId(),
                counter,
                issuedAt);
        verifyCanonicalPayload(request.getSignedPayload(), expectedPayload);
        verifySignature(
                decodeBase64Flexible(credential.getPublicKeyEd25519(), "stored public key"),
                request.getSignedPayload(),
                request.getSignature());
        return counter;
    }

    private DeviceKeyChallengeResponse issueChallenge(
            DeviceKeyChallengePurpose purpose,
            String username,
            Long userId,
            String sessionId) {
        String challengeId = UUID.randomUUID().toString();
        String challenge = randomHex(32);
        long expiresAt = Instant.now().plusSeconds(challengeTtlSeconds).getEpochSecond();
        DeviceKeyChallengeState state = new DeviceKeyChallengeState(
                challengeId,
                challenge,
                purpose,
                username,
                userId,
                sessionId,
                expiresAt);
        try {
            redisService.setValue(
                    CHALLENGE_PREFIX + challengeId,
                    objectMapper.writeValueAsString(state),
                    challengeTtlSeconds);
        } catch (Exception exception) {
            throw new DeviceKeyProtocolException("Unable to store device key challenge.", exception);
        }
        return new DeviceKeyChallengeResponse(
                challengeId,
                challenge,
                challengeTtlSeconds,
                onionServiceId,
                ALGORITHM,
                CANONICALIZATION);
    }

    private DeviceKeyChallengeState consumeChallenge(String challengeId) {
        requireNonBlank(challengeId, "challengeId is required.");
        String raw = redisService.getAndDeleteValue(CHALLENGE_PREFIX + challengeId.trim());
        if (raw == null || raw.isBlank()) {
            throw new DeviceKeyChallengeException("Challenge expired, missing, or already used.");
        }
        try {
            DeviceKeyChallengeState state = objectMapper.readValue(raw, DeviceKeyChallengeState.class);
            if (state.expiresAtEpochSeconds() < Instant.now().getEpochSecond()) {
                throw new DeviceKeyChallengeException("Challenge expired.");
            }
            return state;
        } catch (DeviceKeyChallengeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DeviceKeyProtocolException("Stored challenge is invalid.", exception);
        }
    }

    private Map<String, Object> registrationPayload(
            DeviceKeyChallengeState challenge,
            String credentialId,
            String deviceInstallId,
            String publicKeySha256,
            long counter,
            long issuedAt) {
        return Map.ofEntries(
                Map.entry("algorithm", ALGORITHM),
                Map.entry("challenge", challenge.challenge()),
                Map.entry("challengeId", challenge.challengeId()),
                Map.entry("counter", counter),
                Map.entry("credentialId", credentialId),
                Map.entry("deviceInstallId", deviceInstallId),
                Map.entry("issuedAtEpochSeconds", issuedAt),
                Map.entry("onionServiceId", onionServiceId),
                Map.entry("publicKeySha256", publicKeySha256),
                Map.entry("sessionId", normalizeRequired(challenge.sessionId(), "")),
                Map.entry("type", REGISTER_TYPE),
                Map.entry("username", normalizeUsername(challenge.username())),
                Map.entry("version", 1));
    }

    private Map<String, Object> authenticationPayload(
            DeviceKeyChallengeState challenge,
            String username,
            String credentialId,
            String deviceInstallId,
            long counter,
            long issuedAt) {
        return Map.ofEntries(
                Map.entry("challenge", challenge.challenge()),
                Map.entry("challengeId", challenge.challengeId()),
                Map.entry("counter", counter),
                Map.entry("credentialId", credentialId),
                Map.entry("deviceInstallId", deviceInstallId),
                Map.entry("issuedAtEpochSeconds", issuedAt),
                Map.entry("onionServiceId", onionServiceId),
                Map.entry("type", AUTH_TYPE),
                Map.entry("username", normalizeUsername(username)),
                Map.entry("version", 1));
    }

    private void verifyCanonicalPayload(String signedPayload, Map<String, Object> expectedPayload) {
        requireNonBlank(signedPayload, "signedPayload is required.");
        String expectedCanonical = DeviceKeyCanonicalJson.canonicalize(expectedPayload);
        if (!MessageDigest.isEqual(
                expectedCanonical.getBytes(StandardCharsets.UTF_8),
                signedPayload.getBytes(StandardCharsets.UTF_8))) {
            throw new DeviceKeyProtocolException("signedPayload is not the expected canonical JSON.");
        }
        Object algorithm = expectedPayload.get("algorithm");
        if (algorithm != null && !ALGORITHM.equals(algorithm.toString())) {
            throw new DeviceKeyProtocolException("Unsupported device key algorithm.");
        }
        if (!Objects.equals(onionServiceId, expectedPayload.get("onionServiceId"))) {
            throw new DeviceKeyProtocolException("onionServiceId mismatch.");
        }
    }

    private LinkedHashMap<String, Object> parsePayload(String signedPayload) {
        requireNonBlank(signedPayload, "signedPayload is required.");
        try {
            LinkedHashMap<String, Object> payload = objectMapper.readValue(signedPayload, PAYLOAD_TYPE);
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                if (entry.getValue() == null
                        || entry.getValue() instanceof Map<?, ?>
                        || entry.getValue() instanceof Iterable<?>) {
                    throw new DeviceKeyProtocolException("Canonical JSON v1 only allows simple values.");
                }
            }
            return payload;
        } catch (DeviceKeyProtocolException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DeviceKeyProtocolException("signedPayload is not valid JSON.", exception);
        }
    }

    private void verifySignature(byte[] publicKeyBytes, String signedPayload, String signatureBase64Url) {
        requireNonBlank(signatureBase64Url, "signature is required.");
        try {
            PublicKey publicKey = loadEd25519PublicKey(publicKeyBytes);
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(signedPayload.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(decodeBase64Flexible(signatureBase64Url, "signature"))) {
                throw new DeviceKeySignatureException("Device key signature rejected.");
            }
        } catch (DeviceKeySignatureException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DeviceKeySignatureException("Unable to verify device key signature.", exception);
        }
    }

    private PublicKey loadEd25519PublicKey(byte[] rawKey) throws Exception {
        if (rawKey.length != 32) {
            throw new IllegalArgumentException("Ed25519 public key must be exactly 32 bytes.");
        }
        byte[] x509Key = new byte[ED25519_X509_PREFIX.length + rawKey.length];
        System.arraycopy(ED25519_X509_PREFIX, 0, x509Key, 0, ED25519_X509_PREFIX.length);
        System.arraycopy(rawKey, 0, x509Key, ED25519_X509_PREFIX.length, rawKey.length);
        return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(x509Key));
    }

    private void validateIssuedAt(long issuedAtEpochSeconds) {
        long now = Instant.now().getEpochSecond();
        if (issuedAtEpochSeconds <= 0 || issuedAtEpochSeconds > now + 30) {
            throw new DeviceKeyProtocolException("issuedAtEpochSeconds is invalid.");
        }
    }

    private long longField(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new DeviceKeyProtocolException(field + " must be an integer.");
    }

    private String stringField(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value instanceof String string && !string.isBlank()) {
            return string.trim();
        }
        throw new DeviceKeyProtocolException(field + " must be a non-empty string.");
    }

    private byte[] decodeBase64Flexible(String value, String label) {
        requireNonBlank(value, label + " is required.");
        String normalized = value.trim();
        try {
            return Base64.getUrlDecoder().decode(padBase64(normalized));
        } catch (IllegalArgumentException ignored) {
            try {
                return Base64.getDecoder().decode(normalized);
            } catch (IllegalArgumentException exception) {
                throw new DeviceKeyProtocolException(label + " is not valid base64.", exception);
            }
        }
    }

    private String padBase64(String value) {
        int remainder = value.length() % 4;
        return remainder == 0 ? value : value + "=".repeat(4 - remainder);
    }

    private String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DeviceKeyProtocolException(message);
        }
    }

    public record VerifiedDeviceKeyRegistration(
            String credentialId,
            String userHandle,
            String publicKeyEd25519,
            String publicKeySha256,
            long counter,
            String deviceName,
            String deviceInstallId,
            String keyStorage,
            String platform,
            String browser,
            String brand,
            String model,
            String serialNumber,
            String onionServiceId) {
    }
}
