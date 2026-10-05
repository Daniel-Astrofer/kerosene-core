package com.kerosene.auth.application.service.passkey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.kerosene.auth.application.service.cache.contracts.RedisServicer;
import com.kerosene.common.infra.logging.LogSanitizer;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Verifies WebAuthn challenges and Ed25519 assertions against configured origins and RP IDs. */
@Service
public class PasskeyService {

    /** Logger for passkey diagnostics; request identity values are fingerprinted. */
    private static final Logger log = LoggerFactory.getLogger(PasskeyService.class);
    /** Base64URL decoder for WebAuthn fields. */
    private static final Base64.Decoder B64_URL_DECODER = Base64.getUrlDecoder();
    /** Unpadded Base64URL encoder for challenge values. */
    private static final Base64.Encoder B64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    /** Hexadecimal codec for server-side challenges and fingerprints. */
    private static final HexFormat HEX = HexFormat.of();
    /** X.509 SubjectPublicKeyInfo prefix used to wrap raw 32-byte Ed25519 keys. */
    private static final byte[] ED25519_X509_PREFIX = new byte[] {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };
    /** Maximum number of parsed Ed25519 keys kept in memory. */
    private static final int PUBLIC_KEY_CACHE_MAX_SIZE = 4096;
    /** Maximum number of RP ID hashes cached in memory. */
    private static final int RP_ID_HASH_CACHE_MAX_SIZE = 1024;
    /** Fallback relying-party identifier for blank configuration. */
    private static final String DEFAULT_RELYING_PARTY_ID = "kerosene-device";
    /** Thread-confined SHA-256 instance to avoid sharing mutable digest state. */
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    });
    /** Thread-confined Ed25519 key factory, with provider alias fallback. */
    private static final ThreadLocal<KeyFactory> ED25519_KEY_FACTORY = ThreadLocal.withInitial(() -> {
        try {
            return KeyFactory.getInstance("Ed25519");
        } catch (Exception e) {
            try {
                return KeyFactory.getInstance("EdDSA");
            } catch (Exception fallback) {
                throw new IllegalStateException("Ed25519 KeyFactory is not available.", fallback);
            }
        }
    });
    /** Thread-confined Ed25519 verifier, with provider alias fallback. */
    private static final ThreadLocal<Signature> ED25519_SIGNATURE = ThreadLocal.withInitial(() -> {
        try {
            return Signature.getInstance("Ed25519");
        } catch (Exception e) {
            try {
                return Signature.getInstance("EdDSA");
            } catch (Exception fallback) {
                throw new IllegalStateException("Ed25519 Signature is not available.", fallback);
            }
        }
    });
    /** Redis boundary for expiring and one-time passkey challenges. */
    private final RedisServicer redisService;
    /** Cryptographically secure random source for challenges. */
    private final SecureRandom secureRandom = new SecureRandom();
    /** Redis key namespace for passkey challenges. */
    private static final String CHALLENGE_PREFIX = "passkey_challenge:";
    /** Exact client origins allowed to submit WebAuthn proofs. */
    private final Set<String> allowedOrigins;
    /** Configured relying-party identifier used for authenticator data checks. */
    private final String relyingPartyId;
    /** Bounded cache of parsed public keys, keyed by public-key fingerprint. */
    private final ConcurrentHashMap<String, PublicKey> publicKeyCache = new ConcurrentHashMap<>();
    /** Bounded cache of RP ID hashes used to validate authenticator data. */
    private final ConcurrentHashMap<String, byte[]> rpIdHashCache = new ConcurrentHashMap<>();

    /** JSON mapper for clientDataJSON. */
    private final ObjectMapper jsonMapper;
    /** CBOR mapper for COSE public keys. */
    private final ObjectMapper cborMapper;

    /** Expiration in seconds for generated challenges. */
    @Value("${webauthn.challenge-ttl-seconds:90}")
    private long challengeTtlSeconds = 90L;

    /** Creates the service with configured origin allowlist, relying party, and data decoders. */
    /** @param redisService challenge storage */
    /** @param jsonMapper mapper for client JSON */
    /** @param cborMapper mapper for COSE CBOR */
    /** @param allowedOrigins comma-separated allowed origins */
    /** @param relyingPartyId configured RP identifier, or fallback when blank */
    public PasskeyService(
            RedisServicer redisService,
            ObjectMapper jsonMapper,
            @Qualifier("cborObjectMapper") ObjectMapper cborMapper,
            @Value("${webauthn.origins:}") String allowedOrigins,
            @Value("${webauthn.relying-party-id:kerosene-device}") String relyingPartyId) {
        this.redisService = redisService;
        this.jsonMapper = jsonMapper;
        this.cborMapper = cborMapper;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.relyingPartyId = relyingPartyId == null || relyingPartyId.isBlank()
                ? DEFAULT_RELYING_PARTY_ID
                : relyingPartyId.trim();
    }

    /** Generates 32 random bytes, stores their hex form with a TTL, and returns the challenge. */
    /** @param username subject used to namespace the challenge key */
    /** @return lowercase hexadecimal challenge */
    public String generateChallenge(String username) {
        byte[] challenge = new byte[32];
        secureRandom.nextBytes(challenge);
        String challengeHex = HEX.formatHex(challenge);

        redisService.setValue(
                CHALLENGE_PREFIX + normalizeChallengeSubject(username),
                challengeHex,
                effectiveChallengeTtlSeconds());
        return challengeHex;
    }

    /** Retrieves and atomically removes the subject's current challenge. */
    /** @param username challenge subject */
    /** @return consumed challenge or null when absent */
    public String consumeChallengeFromRedis(String username) {
        return redisService.getAndDeleteValue(CHALLENGE_PREFIX + normalizeChallengeSubject(username));
    }

    /** Reads a challenge without consuming it. */
    /** @param username challenge subject */
    /** @return challenge or null when absent */
    public String getChallengeFromRedis(String username) {
        return redisService.getValue(CHALLENGE_PREFIX + normalizeChallengeSubject(username));
    }

    /** Deletes any outstanding challenge for a subject. */
    /** @param username challenge subject */
    public void deleteChallengeFromRedis(String username) {
        redisService.deleteValue(CHALLENGE_PREFIX + normalizeChallengeSubject(username));
    }

    /**
     * Verifies the WebAuthn signature manually.
     * @param username The username.
     * @param expectedChallengeHex The challenge generated by the server.
     * @param signatureB64Url Base64URL encoded signature.
     * @param publicKeyBytes The public key in COSE/CBOR format.
     * @param authDataB64Url Base64URL encoded authenticatorData.
     * @param clientDataJsonB64Url Base64URL encoded clientDataJSON.
     * @return true when challenge, origin, type, RP binding, key and signature validate
     */
    public boolean verifySignature(String username, String expectedChallengeHex, String signatureB64Url, byte[] publicKeyBytes,
                                   String authDataB64Url, String clientDataJsonB64Url) {
        return verifySignatureInternal(
                username,
                expectedChallengeHex,
                signatureB64Url,
                publicKeyBytes,
                authDataB64Url,
                clientDataJsonB64Url,
                Set.of("webauthn.get", "webauthn.create"));
    }

    /** Verifies an authentication assertion and accepts only the {@code webauthn.get} operation type. */
    /** @param username challenge subject */
    /** @param expectedChallengeHex server-issued challenge in hexadecimal */
    /** @param signatureB64Url encoded Ed25519 assertion signature */
    /** @param publicKeyBytes raw or COSE-encoded public key */
    /** @param authDataB64Url encoded authenticator data */
    /** @param clientDataJsonB64Url encoded WebAuthn client data */
    /** @return true when the assertion verifies */
    public boolean verifyAuthenticationSignature(String username, String expectedChallengeHex, String signatureB64Url,
            byte[] publicKeyBytes, String authDataB64Url, String clientDataJsonB64Url) {
        return verifySignatureInternal(
                username,
                expectedChallengeHex,
                signatureB64Url,
                publicKeyBytes,
                authDataB64Url,
                clientDataJsonB64Url,
                Set.of("webauthn.get"));
    }

    /** Verifies authentication and returns the parsed signature counter and matched RP ID. */
    /** @param username challenge subject */
    /** @param expectedChallengeHex server-issued challenge */
    /** @param signatureB64Url encoded signature */
    /** @param publicKeyBytes raw or COSE-encoded public key */
    /** @param authDataB64Url encoded authenticator data */
    /** @param clientDataJsonB64Url encoded WebAuthn client data */
    /** @return verification state, counter and RP ID; failures use a negative counter */
    public PasskeyVerificationResult verifyAuthenticationAssertion(String username, String expectedChallengeHex,
            String signatureB64Url, byte[] publicKeyBytes, String authDataB64Url, String clientDataJsonB64Url) {
        return verifyAssertionInternal(
                username,
                expectedChallengeHex,
                signatureB64Url,
                publicKeyBytes,
                authDataB64Url,
                clientDataJsonB64Url,
                Set.of("webauthn.get"));
    }

    /** Verifies a registration proof and accepts only the {@code webauthn.create} operation type. */
    /** @param username challenge subject */
    /** @param expectedChallengeHex server-issued challenge */
    /** @param signatureB64Url encoded signature */
    /** @param publicKeyBytes raw or COSE-encoded public key */
    /** @param authDataB64Url encoded authenticator data */
    /** @param clientDataJsonB64Url encoded WebAuthn client data */
    /** @return true when the registration proof verifies */
    public boolean verifyRegistrationSignature(String username, String expectedChallengeHex, String signatureB64Url,
            byte[] publicKeyBytes, String authDataB64Url, String clientDataJsonB64Url) {
        return verifySignatureInternal(
                username,
                expectedChallengeHex,
                signatureB64Url,
                publicKeyBytes,
                authDataB64Url,
                clientDataJsonB64Url,
                Set.of("webauthn.create"));
    }

    /** Converts the detailed verification result into a boolean for compatibility callers. */
    /** @param username challenge subject */
    /** @param expectedChallengeHex expected challenge */
    /** @param signatureB64Url encoded signature */
    /** @param publicKeyBytes public key material */
    /** @param authDataB64Url encoded authenticator data */
    /** @param clientDataJsonB64Url encoded client data */
    /** @param expectedTypes accepted WebAuthn operation types */
    /** @return whether verification succeeds */
    private boolean verifySignatureInternal(String username, String expectedChallengeHex, String signatureB64Url,
            byte[] publicKeyBytes, String authDataB64Url, String clientDataJsonB64Url, Set<String> expectedTypes) {
        return verifyAssertionInternal(
                username,
                expectedChallengeHex,
                signatureB64Url,
                publicKeyBytes,
                authDataB64Url,
                clientDataJsonB64Url,
                expectedTypes).verified();
    }

    /** Checks challenge, origin, operation type, RP hash and Ed25519 signature; any parse/crypto error fails closed. */
    /** @param username subject used in sanitized diagnostics */
    /** @param expectedChallengeHex server-side challenge */
    /** @param signatureB64Url assertion signature */
    /** @param publicKeyBytes raw or COSE public key */
    /** @param authDataB64Url authenticator data */
    /** @param clientDataJsonB64Url client operation data */
    /** @param expectedTypes allowed operation types */
    /** @return detailed verification result or failed sentinel */
    private PasskeyVerificationResult verifyAssertionInternal(String username, String expectedChallengeHex,
            String signatureB64Url, byte[] publicKeyBytes, String authDataB64Url, String clientDataJsonB64Url,
            Set<String> expectedTypes) {
        try {
            if (expectedChallengeHex == null) {
                log.warn("Passkey challenge is missing for userRef={}", LogSanitizer.fingerprint(username));
                return PasskeyVerificationResult.failed();
            }

            byte[] signatureBytes = B64_URL_DECODER.decode(signatureB64Url);
            byte[] authDataBytes = B64_URL_DECODER.decode(authDataB64Url);
            byte[] clientDataBytes = B64_URL_DECODER.decode(clientDataJsonB64Url);

            // 1. Structural JSON Validation: Challenge, Origin, and Type
            JsonNode clientDataNode = jsonMapper.readTree(clientDataBytes);
            String challengeInClientData = clientDataNode.path("challenge").asText(null);
            String typeInClientData = clientDataNode.path("type").asText(null);
            String originInClientData = clientDataNode.path("origin").asText(null);

            byte[] expectedChallengeBytes = hexToBytes(expectedChallengeHex);
            String expectedChallengeB64Url = B64_URL_ENCODER.encodeToString(expectedChallengeBytes);

            if (!expectedChallengeB64Url.equals(challengeInClientData)) {
                log.error("Possible passkey replay attempt: challenge mismatch for userRef={}",
                        LogSanitizer.fingerprint(username));
                return PasskeyVerificationResult.failed();
            }

            if (!isAllowedOrigin(originInClientData)) {
                log.error("Invalid WebAuthn origin for userRef={} originRef={}",
                        LogSanitizer.fingerprint(username),
                        LogSanitizer.fingerprint(originInClientData));
                return PasskeyVerificationResult.failed();
            }

            if (!expectedTypes.contains(typeInClientData)) {
                log.error("Invalid WebAuthn operation type for userRef={}: {}",
                        LogSanitizer.fingerprint(username), typeInClientData);
                return PasskeyVerificationResult.failed();
            }

            String matchedRpId = validateAuthenticatorData(authDataBytes, originInClientData);
            if (matchedRpId == null) {
                return PasskeyVerificationResult.failed();
            }

            byte[] clientDataHash = sha256(clientDataBytes);

            // 3. Concatenação: authenticatorData + SHA256(clientDataJSON)
            byte[] signedData = new byte[authDataBytes.length + clientDataHash.length];
            System.arraycopy(authDataBytes, 0, signedData, 0, authDataBytes.length);
            System.arraycopy(clientDataHash, 0, signedData, authDataBytes.length, clientDataHash.length);

            PublicKey publicKey = loadEd25519PublicKey(publicKeyBytes);

            // 5. Verificar a assinatura contra os dados concatenados
            Signature ed25519 = ED25519_SIGNATURE.get();
            ed25519.initVerify(publicKey);
            ed25519.update(signedData);

            boolean verified = ed25519.verify(signatureBytes);
            log.debug("Passkey signature verification completed for userRef={} verified={}",
                    LogSanitizer.fingerprint(username), verified);

            return new PasskeyVerificationResult(
                    verified,
                    verified ? extractSignatureCount(authDataBytes) : -1L,
                    verified ? matchedRpId : null);

        } catch (Exception e) {
            log.error("Passkey signature verification failed for userRef={}: {}",
                    LogSanitizer.fingerprint(username), e.getMessage());
            return PasskeyVerificationResult.failed();
        }
    }

    /** Decodes authenticator data and extracts its unsigned 32-bit signature counter. */
    /** @param authDataB64Url Base64URL authenticator data */
    /** @return parsed signature count */
    public long extractSignatureCount(String authDataB64Url) {
        byte[] authDataBytes = B64_URL_DECODER.decode(authDataB64Url);
        return extractSignatureCount(authDataBytes);
    }

    /** Reads the big-endian counter at byte offsets 33 through 36 of authenticator data. */
    /** @param authDataBytes decoded data with at least 37 bytes */
    /** @return unsigned counter represented by a long */
    private long extractSignatureCount(byte[] authDataBytes) {
        if (authDataBytes.length < 37) {
            throw new IllegalArgumentException("authenticatorData must be at least 37 bytes.");
        }
        return ((long) authDataBytes[33] & 0xff) << 24
                | ((long) authDataBytes[34] & 0xff) << 16
                | ((long) authDataBytes[35] & 0xff) << 8
                | ((long) authDataBytes[36] & 0xff);
    }

    /**
     * Result of assertion verification, including the counter and RP ID bound by authenticator data.
     * @param verified whether the signature and all protocol checks passed
     * @param signatureCount parsed authenticator counter, or -1 on failure
     * @param relyingPartyId RP ID matched against authenticator data
     */
    public record PasskeyVerificationResult(boolean verified, long signatureCount, String relyingPartyId) {
        /** Creates a result without an RP ID for legacy callers. */
        /** @param verified verification outcome */
        /** @param signatureCount parsed counter or failure sentinel */
        public PasskeyVerificationResult(boolean verified, long signatureCount) {
            this(verified, signatureCount, null);
        }

        /** Creates the standard failed verification sentinel. */
        /** @return unverified result with counter -1 and no RP ID */
        private static PasskeyVerificationResult failed() {
            return new PasskeyVerificationResult(false, -1L, null);
        }
    }

    /** Exposes the normalized host for the current servlet request, when one is bound. */
    /** @return current request host or null outside a servlet request */
    public String resolveCurrentRequestHost() {
        return currentRequestHost();
    }

    /** Chooses the configured RP ID or an allowed dynamic request host according to RP scope. */
    /** @return RP ID to use for the current request */
    public String resolveCurrentRelyingPartyId() {
        if (isApplicationScopedRelyingPartyId()) {
            return relyingPartyId;
        }

        String requestHost = currentRequestHost();
        if (hostMatchesConfiguredRpId(requestHost)) {
            return relyingPartyId;
        }
        if (requestHost != null && isDynamicHostAllowed(requestHost)) {
            return requestHost;
        }
        return relyingPartyId;
    }

    /** Decodes client data and extracts a normalized host from its origin; malformed data yields null. */
    /** @param clientDataJsonB64Url encoded clientDataJSON */
    /** @return parsed origin host or null */
    public String extractOriginHostFromClientData(String clientDataJsonB64Url) {
        try {
            byte[] clientDataBytes = B64_URL_DECODER.decode(clientDataJsonB64Url);
            JsonNode clientDataNode = jsonMapper.readTree(clientDataBytes);
            return extractOriginHost(clientDataNode.path("origin").asText(null));
        } catch (Exception e) {
            return null;
        }
    }

    /** Extracts the raw origin field from clientDataJSON without host normalization. */
    /** @param clientDataJsonB64Url encoded clientDataJSON */
    /** @return raw origin or null for malformed/missing data */
    public String extractOriginFromClientData(String clientDataJsonB64Url) {
        try {
            byte[] clientDataBytes = B64_URL_DECODER.decode(clientDataJsonB64Url);
            JsonNode clientDataNode = jsonMapper.readTree(clientDataBytes);
            return clientDataNode.path("origin").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Checks whether clientDataJSON contains an origin exactly present in the configured allowlist. */
    /** @param clientDataJsonB64Url encoded client data */
    /** @return true only when its origin is allowed */
    public boolean isClientDataOriginAllowed(String clientDataJsonB64Url) {
        return isAllowedOrigin(extractOriginFromClientData(clientDataJsonB64Url));
    }

    /** Resolves the expected RP ID from the client-data origin, falling back to current context on parse failure. */
    /** @param clientDataJsonB64Url encoded client data */
    /** @return expected RP ID */
    public String resolveRelyingPartyIdFromClientData(String clientDataJsonB64Url) {
        try {
            byte[] clientDataBytes = B64_URL_DECODER.decode(clientDataJsonB64Url);
            JsonNode clientDataNode = jsonMapper.readTree(clientDataBytes);
            return resolveExpectedRelyingPartyId(clientDataNode.path("origin").asText(null));
        } catch (Exception e) {
            return resolveCurrentRelyingPartyId();
        }
    }

    /** Matches authenticator RP hash against configured candidates, falling back to client-data resolution. */
    /** @param authDataB64Url encoded authenticator data */
    /** @param clientDataJsonB64Url encoded client data */
    /** @return RP ID inferred from proof or fallback context */
    public String resolveRelyingPartyIdFromAuthenticatorData(String authDataB64Url, String clientDataJsonB64Url) {
        try {
            byte[] authDataBytes = B64_URL_DECODER.decode(authDataB64Url);
            if (authDataBytes.length < 32) {
                return resolveRelyingPartyIdFromClientData(clientDataJsonB64Url);
            }

            byte[] clientDataBytes = B64_URL_DECODER.decode(clientDataJsonB64Url);
            JsonNode clientDataNode = jsonMapper.readTree(clientDataBytes);
            String originInClientData = clientDataNode.path("origin").asText(null);
            byte[] suppliedRpIdHash = Arrays.copyOfRange(authDataBytes, 0, 32);
            String matchedRpId = matchingRelyingPartyId(suppliedRpIdHash, originInClientData);
            return matchedRpId == null ? resolveExpectedRelyingPartyId(originInClientData) : matchedRpId;
        } catch (Exception e) {
            return resolveRelyingPartyIdFromClientData(clientDataJsonB64Url);
        }
    }

    /** Loads a cached or parsed Ed25519 key from raw bytes or COSE/CBOR key material. */
    /** @param publicKeyBytes encoded key material */
    /** @return JCA public key */
    /** @throws Exception when neither raw nor COSE form is a valid Ed25519 key */
    private PublicKey loadEd25519PublicKey(byte[] publicKeyBytes) throws Exception {
        String cacheKey = HEX.formatHex(sha256(publicKeyBytes));
        PublicKey cached = publicKeyCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        PublicKey parsed;
        try {
            parsed = loadRawEd25519PublicKey(publicKeyBytes);
        } catch (Exception e) {
            try {
                parsed = loadRawEd25519PublicKey(extractRawKeyFromCOSE(publicKeyBytes));
            } catch (Exception e2) {
                log.error("Failed to parse public key as raw or COSE/CBOR: {}", e2.getMessage());
                throw e2;
            }
        }

        evictOneIfOversized(publicKeyCache, PUBLIC_KEY_CACHE_MAX_SIZE);
        PublicKey existing = publicKeyCache.putIfAbsent(cacheKey, parsed);
        return existing != null ? existing : parsed;
    }

    /** Wraps exactly 32 raw Ed25519 bytes in the X.509 prefix and parses a JCA key. */
    /** @param rawKey 32-byte public key */
    /** @return parsed public key */
    /** @throws Exception when input length or key parsing is invalid */
    private PublicKey loadRawEd25519PublicKey(byte[] rawKey) throws Exception {
        if (rawKey.length != 32) {
            throw new IllegalArgumentException("Ed25519 public key must be exactly 32 bytes.");
        }
        byte[] x509Key = new byte[ED25519_X509_PREFIX.length + rawKey.length];
        System.arraycopy(ED25519_X509_PREFIX, 0, x509Key, 0, ED25519_X509_PREFIX.length);
        System.arraycopy(rawKey, 0, x509Key, ED25519_X509_PREFIX.length, rawKey.length);

        return ED25519_KEY_FACTORY.get().generatePublic(new X509EncodedKeySpec(x509Key));
    }

    /** Validates COSE kty=OKP and alg=EdDSA, then returns the x-coordinate Ed25519 public bytes. */
    /** @param coseCbor CBOR-encoded COSE key map */
    /** @return raw Ed25519 public key */
    /** @throws Exception when the COSE structure or key type is unsupported */
    private byte[] extractRawKeyFromCOSE(byte[] coseCbor) throws Exception {
        Map<Integer, Object> coseMap = cborMapper.readValue(coseCbor, new CoseIntegerMapType());

        // 1: kty (1 = OKP - Octet Key Pair)
        if (!Integer.valueOf(1).equals(coseMap.get(1))) {
            throw new IllegalArgumentException("Invalid COSE Key Type (kty). Expected OKP (1).");
        }

        // 3: alg (-8 = EdDSA)
        if (!Integer.valueOf(-8).equals(coseMap.get(3))) {
            throw new IllegalArgumentException("Invalid COSE Algorithm (alg). Expected EdDSA (-8).");
        }

        // -1: crv (6 = Ed25519)
        if (!Integer.valueOf(6).equals(coseMap.get(-1))) {
            throw new IllegalArgumentException("Invalid COSE Curve (crv). Expected Ed25519 (6).");
        }

        // -2: x (The 32-byte public key)
        Object xObj = coseMap.get(-2);
        byte[] rawKey;
        if (xObj instanceof byte[]) {
            rawKey = (byte[]) xObj;
        } else if (xObj instanceof String) {
            rawKey = B64_URL_DECODER.decode((String) xObj);
        } else {
            throw new IllegalArgumentException("Could not find raw public key (x) in COSE map");
        }

        if (rawKey.length != 32) {
            throw new IllegalArgumentException("Ed25519 public key (x) must be exactly 32 bytes.");
        }

        return rawKey;
    }

    /** Validates the authenticator RP ID hash and returns the matching RP ID. */
    /** @param authDataBytes decoded authenticator data */
    /** @param originInClientData origin extracted from client data */
    /** @return matched RP ID, or null when no candidate matches */
    /** @throws Exception when authenticator data is malformed */
    private String validateAuthenticatorData(byte[] authDataBytes, String originInClientData) throws Exception {
        if (authDataBytes == null || authDataBytes.length < 37) {
            log.error("Invalid authenticatorData length: {}", authDataBytes == null ? "null" : authDataBytes.length);
            return null;
        }

        byte[] suppliedRpIdHash = Arrays.copyOfRange(authDataBytes, 0, 32);
        String matchedRpId = matchingRelyingPartyId(suppliedRpIdHash, originInClientData);
        if (matchedRpId == null) {
            log.error("Invalid authenticatorData rpIdHash for rpId {}", resolveExpectedRelyingPartyId(originInClientData));
            return null;
        }

        int flags = authDataBytes[32] & 0xff;
        boolean userPresent = (flags & 0x01) != 0;
        boolean userVerified = (flags & 0x04) != 0;
        if (!userPresent || !userVerified) {
            log.error("Authenticator did not assert both user presence and verification. flags={}", flags);
            return null;
        }

        return matchedRpId;
    }

    /** Checks the supplied RP hash against candidate RP identifiers derived from configuration and origin. */
    /** @param suppliedRpIdHash first 32 bytes of authenticator data */
    /** @param originInClientData client-data origin */
    /** @return matching RP ID or null */
    /** @throws Exception when hashing candidate identifiers fails */
    private String matchingRelyingPartyId(byte[] suppliedRpIdHash, String originInClientData) throws Exception {
        for (String candidateRpId : candidateRelyingPartyIds(originInClientData)) {
            if (MessageDigest.isEqual(rpIdHash(candidateRpId), suppliedRpIdHash)) {
                return candidateRpId;
            }
        }
        return null;
    }

    /** Builds insertion-ordered RP candidates from configured ID, expected origin ID, and request context. */
    /** @param originInClientData client-data origin */
    /** @return distinct nonblank candidates in preference order */
    private Set<String> candidateRelyingPartyIds(String originInClientData) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addRelyingPartyIdCandidate(candidates, resolveExpectedRelyingPartyId(originInClientData));
        addRelyingPartyIdCandidate(candidates, relyingPartyId);

        String requestHost = currentRequestHost();
        if (requestHost != null && isDynamicHostAllowed(requestHost)) {
            addRelyingPartyIdCandidate(candidates, requestHost);
        }

        String originHost = extractOriginHost(originInClientData);
        if (originHost != null && isDynamicHostAllowed(originHost)) {
            addRelyingPartyIdCandidate(candidates, originHost);
        }

        return candidates;
    }

    /** Adds one trimmed RP candidate when it contains text and is not already present. */
    /** @param candidates ordered candidate set */
    /** @param value candidate RP identifier */
    private void addRelyingPartyIdCandidate(Set<String> candidates, String value) {
        if (value != null && !value.isBlank()) {
            candidates.add(value.trim());
        }
    }

    /** Requires the raw origin to be a configured exact allowlist member. */
    /** @param originInClientData origin claim */
    /** @return true when configured and matched exactly */
    private boolean isAllowedOrigin(String originInClientData) {
        if (originInClientData == null || originInClientData.isBlank()) {
            return false;
        }
        if (allowedOrigins.contains(originInClientData)) {
            return true;
        }

        String originHost = extractOriginHost(originInClientData);
        String requestHost = currentRequestHost();
        return requestHost != null
                && isDynamicHostAllowed(requestHost)
                && requestHost.equals(originHost);
    }

    /** Selects the RP expected for an origin, preferring an application RP or validated host. */
    /** @param originInClientData origin claim */
    /** @return RP identifier expected for authenticator data */
    private String resolveExpectedRelyingPartyId(String originInClientData) {
        String requestHost = currentRequestHost();
        String originHost = extractOriginHost(originInClientData);

        if (isApplicationScopedRelyingPartyId() && isAllowedOrigin(originInClientData)) {
            return relyingPartyId;
        }
        if (hostMatchesConfiguredRpId(requestHost) || hostMatchesConfiguredRpId(originHost)) {
            return relyingPartyId;
        }
        if (requestHost != null && isDynamicHostAllowed(requestHost)) {
            return requestHost;
        }
        if (originHost != null && isDynamicHostAllowed(originHost)) {
            return originHost;
        }
        return relyingPartyId;
    }

    /** Checks whether a normalized request host equals or is a subdomain of the configured DNS RP ID. */
    /** @param host normalized host */
    /** @return true when host belongs to the configured RP domain */
    private boolean hostMatchesConfiguredRpId(String host) {
        if (host == null || relyingPartyId == null || relyingPartyId.isBlank()) {
            return false;
        }
        String normalizedRpId = relyingPartyId.toLowerCase(Locale.ROOT);
        return host.equals(normalizedRpId) || host.endsWith("." + normalizedRpId);
    }

    /** Allows a dynamic host only when an allowlisted HTTPS origin uses that host. */
    /** @param host candidate request host */
    /** @return true when an allowed HTTPS origin matches it */
    private boolean isDynamicHostAllowed(String host) {
        return host.endsWith(".onion")
                || "localhost".equals(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
    }

    /** Identifies RP identifiers scoped to an application rather than a DNS hostname. */
    /** @return true when configured identifier has no dot or colon */
    private boolean isApplicationScopedRelyingPartyId() {
        String normalized = relyingPartyId == null ? "" : relyingPartyId.trim().toLowerCase(Locale.ROOT);
        return !normalized.isBlank()
                && !normalized.contains(".")
                && !isDynamicHostAllowed(normalized);
    }

    /** Reads the bound servlet request and selects host metadata, respecting forwarded host input. */
    /** @return normalized current host or null when no servlet request is available */
    private String currentRequestHost() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }

        HttpServletRequest request = servletAttributes.getRequest();
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (forwardedHost != null && !forwardedHost.isBlank()) {
            String normalized = normalizeHost(forwardedHost.split(",")[0]);
            if (normalized != null) {
                return normalized;
            }
        }

        String serverName = normalizeHost(request.getServerName());
        if (serverName != null) {
            return serverName;
        }

        return normalizeHost(request.getHeader("Host"));
    }

    /** Extracts and normalizes a host from an origin URI; non-URI Android tokens are retained normalized. */
    /** @param originInClientData raw origin value */
    /** @return normalized host/token or null when absent */
    private String extractOriginHost(String originInClientData) {
        if (originInClientData == null || originInClientData.isBlank()) {
            return null;
        }
        String origin = originInClientData.trim();
        // Android sovereign passkey origin is not an HTTP URL (e.g. android:apk-key-hash:kerosene).
        // Persist a stable host token instead of URI.getHost() which returns null for this scheme.
        if (origin.regionMatches(true, 0, "android:", 0, "android:".length())) {
            String withoutScheme = origin.substring("android:".length()).trim();
            if (withoutScheme.isEmpty()) {
                return "android";
            }
            return withoutScheme.toLowerCase(Locale.ROOT);
        }
        try {
            return normalizeHost(URI.create(origin).getHost());
        } catch (Exception ignored) {
            return origin.toLowerCase(Locale.ROOT);
        }
    }

    /** Trims scheme/path/port decoration from host-like input and lowercases it using ROOT locale. */
    /** @param host raw host, forwarded host, or origin-derived candidate */
    /** @return normalized host or null when blank */
    private String normalizeHost(String host) {
        if (host == null) {
            return null;
        }

        String normalized = host.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.startsWith("[")) {
            int closingBracket = normalized.indexOf(']');
            if (closingBracket > 0) {
                normalized = normalized.substring(1, closingBracket);
            }
        } else {
            int portSeparator = normalized.indexOf(':');
            if (portSeparator >= 0) {
                normalized = normalized.substring(0, portSeparator);
            }
        }

        normalized = normalized.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    /** Decodes a hexadecimal challenge to bytes. */
    /** @param hex server-side challenge text */
    /** @return decoded challenge bytes */
    private byte[] hexToBytes(String hex) {
        return HEX.parseHex(hex);
    }

    /** Normalizes a username for a stable per-subject Redis challenge key. */
    /** @param username submitted challenge subject */
    /** @return trimmed lowercase subject or empty text for null */
    private String normalizeChallengeSubject(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    /** Uses the configured positive challenge TTL, falling back to 90 seconds otherwise. */
    /** @return effective expiry in seconds */
    private long effectiveChallengeTtlSeconds() {
        return challengeTtlSeconds > 0 ? challengeTtlSeconds : 90L;
    }

    /** Computes SHA-256 using the current thread's digest instance. */
    /** @param input bytes to hash */
    /** @return 32-byte digest */
    private byte[] sha256(byte[] input) {
        MessageDigest digest = SHA_256.get();
        digest.reset();
        return digest.digest(input);
    }

    /** Returns a cached or newly computed SHA-256 hash of an RP ID. */
    /** @param rpId RP identifier */
    /** @return RP ID hash */
    private byte[] rpIdHash(String rpId) {
        byte[] cached = rpIdHashCache.get(rpId);
        if (cached != null) {
            return cached;
        }
        byte[] computed = sha256(rpId.getBytes(StandardCharsets.UTF_8));
        evictOneIfOversized(rpIdHashCache, RP_ID_HASH_CACHE_MAX_SIZE);
        byte[] existing = rpIdHashCache.putIfAbsent(rpId, computed);
        return existing != null ? existing : computed;
    }

    /** Removes one arbitrary cache entry when a bounded concurrent cache reaches its size limit. */
    /** @param cache concurrent cache to trim */
    /** @param maxSize maximum number of retained entries */
    private <T> void evictOneIfOversized(ConcurrentHashMap<String, T> cache, int maxSize) {
        if (cache.size() < maxSize) {
            return;
        }
        cache.keySet().stream().findAny().ifPresent(cache::remove);
    }

    /** Jackson map type used to decode integer-keyed COSE structures. */
    private static final class CoseIntegerMapType extends TypeReference<Map<Integer, Object>> {
    }
}
