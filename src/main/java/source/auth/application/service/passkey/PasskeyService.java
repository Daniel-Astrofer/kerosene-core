package source.auth.application.service.passkey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.common.infra.logging.LogSanitizer;

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

@Service
public class PasskeyService {

    private static final Logger log = LoggerFactory.getLogger(PasskeyService.class);
    private static final Base64.Decoder B64_URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder B64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final HexFormat HEX = HexFormat.of();
    private static final byte[] ED25519_X509_PREFIX = new byte[] {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };
    private static final int PUBLIC_KEY_CACHE_MAX_SIZE = 4096;
    private static final int RP_ID_HASH_CACHE_MAX_SIZE = 1024;
    private static final String DEFAULT_RELYING_PARTY_ID = "kerosene-device";
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    });
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
    private final RedisServicer redisService;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final String CHALLENGE_PREFIX = "passkey_challenge:";
    private final Set<String> allowedOrigins;
    private final String relyingPartyId;
    private final ConcurrentHashMap<String, PublicKey> publicKeyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, byte[]> rpIdHashCache = new ConcurrentHashMap<>();

    private final ObjectMapper jsonMapper;
    private final ObjectMapper cborMapper;

    @Value("${webauthn.challenge-ttl-seconds:90}")
    private long challengeTtlSeconds = 90L;

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

    public String consumeChallengeFromRedis(String username) {
        return redisService.getAndDeleteValue(CHALLENGE_PREFIX + normalizeChallengeSubject(username));
    }

    public String getChallengeFromRedis(String username) {
        return redisService.getValue(CHALLENGE_PREFIX + normalizeChallengeSubject(username));
    }

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

    public long extractSignatureCount(String authDataB64Url) {
        byte[] authDataBytes = B64_URL_DECODER.decode(authDataB64Url);
        return extractSignatureCount(authDataBytes);
    }

    private long extractSignatureCount(byte[] authDataBytes) {
        if (authDataBytes.length < 37) {
            throw new IllegalArgumentException("authenticatorData must be at least 37 bytes.");
        }
        return ((long) authDataBytes[33] & 0xff) << 24
                | ((long) authDataBytes[34] & 0xff) << 16
                | ((long) authDataBytes[35] & 0xff) << 8
                | ((long) authDataBytes[36] & 0xff);
    }

    public record PasskeyVerificationResult(boolean verified, long signatureCount, String relyingPartyId) {
        public PasskeyVerificationResult(boolean verified, long signatureCount) {
            this(verified, signatureCount, null);
        }

        private static PasskeyVerificationResult failed() {
            return new PasskeyVerificationResult(false, -1L, null);
        }
    }

    public String resolveCurrentRequestHost() {
        return currentRequestHost();
    }

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

    public String extractOriginHostFromClientData(String clientDataJsonB64Url) {
        try {
            byte[] clientDataBytes = B64_URL_DECODER.decode(clientDataJsonB64Url);
            JsonNode clientDataNode = jsonMapper.readTree(clientDataBytes);
            return extractOriginHost(clientDataNode.path("origin").asText(null));
        } catch (Exception e) {
            return null;
        }
    }

    public String extractOriginFromClientData(String clientDataJsonB64Url) {
        try {
            byte[] clientDataBytes = B64_URL_DECODER.decode(clientDataJsonB64Url);
            JsonNode clientDataNode = jsonMapper.readTree(clientDataBytes);
            return clientDataNode.path("origin").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isClientDataOriginAllowed(String clientDataJsonB64Url) {
        return isAllowedOrigin(extractOriginFromClientData(clientDataJsonB64Url));
    }

    public String resolveRelyingPartyIdFromClientData(String clientDataJsonB64Url) {
        try {
            byte[] clientDataBytes = B64_URL_DECODER.decode(clientDataJsonB64Url);
            JsonNode clientDataNode = jsonMapper.readTree(clientDataBytes);
            return resolveExpectedRelyingPartyId(clientDataNode.path("origin").asText(null));
        } catch (Exception e) {
            return resolveCurrentRelyingPartyId();
        }
    }

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

    private PublicKey loadRawEd25519PublicKey(byte[] rawKey) throws Exception {
        if (rawKey.length != 32) {
            throw new IllegalArgumentException("Ed25519 public key must be exactly 32 bytes.");
        }
        byte[] x509Key = new byte[ED25519_X509_PREFIX.length + rawKey.length];
        System.arraycopy(ED25519_X509_PREFIX, 0, x509Key, 0, ED25519_X509_PREFIX.length);
        System.arraycopy(rawKey, 0, x509Key, ED25519_X509_PREFIX.length, rawKey.length);

        return ED25519_KEY_FACTORY.get().generatePublic(new X509EncodedKeySpec(x509Key));
    }

    private byte[] extractRawKeyFromCOSE(byte[] coseCbor) throws Exception {
        Map<Integer, Object> coseMap = cborMapper.readValue(coseCbor, new TypeReference<Map<Integer, Object>>() {});

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

    private String matchingRelyingPartyId(byte[] suppliedRpIdHash, String originInClientData) throws Exception {
        for (String candidateRpId : candidateRelyingPartyIds(originInClientData)) {
            if (MessageDigest.isEqual(rpIdHash(candidateRpId), suppliedRpIdHash)) {
                return candidateRpId;
            }
        }
        return null;
    }

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

    private void addRelyingPartyIdCandidate(Set<String> candidates, String value) {
        if (value != null && !value.isBlank()) {
            candidates.add(value.trim());
        }
    }

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

    private boolean hostMatchesConfiguredRpId(String host) {
        if (host == null || relyingPartyId == null || relyingPartyId.isBlank()) {
            return false;
        }
        String normalizedRpId = relyingPartyId.toLowerCase(Locale.ROOT);
        return host.equals(normalizedRpId) || host.endsWith("." + normalizedRpId);
    }

    private boolean isDynamicHostAllowed(String host) {
        return host.endsWith(".onion")
                || "localhost".equals(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
    }

    private boolean isApplicationScopedRelyingPartyId() {
        String normalized = relyingPartyId == null ? "" : relyingPartyId.trim().toLowerCase(Locale.ROOT);
        return !normalized.isBlank()
                && !normalized.contains(".")
                && !isDynamicHostAllowed(normalized);
    }

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

    private String extractOriginHost(String originInClientData) {
        try {
            return normalizeHost(URI.create(originInClientData).getHost());
        } catch (Exception ignored) {
            return null;
        }
    }

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

    private byte[] hexToBytes(String hex) {
        return HEX.parseHex(hex);
    }

    private String normalizeChallengeSubject(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private long effectiveChallengeTtlSeconds() {
        return challengeTtlSeconds > 0 ? challengeTtlSeconds : 90L;
    }

    private byte[] sha256(byte[] input) {
        MessageDigest digest = SHA_256.get();
        digest.reset();
        return digest.digest(input);
    }

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

    private <T> void evictOneIfOversized(ConcurrentHashMap<String, T> cache, int maxSize) {
        if (cache.size() < maxSize) {
            return;
        }
        cache.keySet().stream().findAny().ifPresent(cache::remove);
    }
}
