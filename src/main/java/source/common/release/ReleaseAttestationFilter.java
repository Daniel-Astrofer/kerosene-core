package source.common.release;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import source.security.CachedBodyHttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Component
public class ReleaseAttestationFilter extends OncePerRequestFilter {

    public static final String RELEASE_DIGEST_HEADER = "X-Kerosene-Release-Digest";
    public static final String RELEASE_TIMESTAMP_HEADER = "X-Kerosene-Release-Timestamp";
    public static final String RELEASE_PROOF_HEADER = "X-Kerosene-Release-Proof";
    public static final String SERVICE_IDENTITY_HEADER = "X-Kerosene-Service-Identity";

    private final ReleaseManifestService releaseManifestService;
    private final boolean enabled;
    private final boolean requireClientCertificate;
    private final long maxClockSkewSeconds;
    private final String sharedSecret;
    private final String requiredCertificateSubject;
    private final List<String> criticalPathPrefixes;

    public ReleaseAttestationFilter(
            ObjectProvider<ReleaseManifestService> releaseManifestService,
            @Value("${release.attestation.remote.enabled:false}") boolean enabled,
            @Value("${release.attestation.require-client-certificate:false}") boolean requireClientCertificate,
            @Value("${release.attestation.max-clock-skew-seconds:300}") long maxClockSkewSeconds,
            @Value("${release.attestation.shared-secret:}") String sharedSecret,
            @Value("${release.attestation.client-certificate-subject:}") String requiredCertificateSubject,
            @Value("${release.attestation.critical-path-prefixes:/internal,/sovereignty/heartbeat,/v1/audit/siphon}") String criticalPathPrefixes) {
        this.releaseManifestService = releaseManifestService.getIfAvailable();
        this.enabled = enabled;
        this.requireClientCertificate = requireClientCertificate;
        this.maxClockSkewSeconds = Math.max(30L, maxClockSkewSeconds);
        this.sharedSecret = sharedSecret != null ? sharedSecret.trim() : "";
        this.requiredCertificateSubject = requiredCertificateSubject != null ? requiredCertificateSubject.trim() : "";
        this.criticalPathPrefixes = Arrays.stream(criticalPathPrefixes.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || criticalPathPrefixes.stream().noneMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CachedBodyHttpServletRequest cachedRequest = request instanceof CachedBodyHttpServletRequest cached
                ? cached
                : new CachedBodyHttpServletRequest(request);

        AttestationFailure failure = validate(cachedRequest);
        if (failure != null) {
            response.setStatus(failure.status.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"error\":\"" + failure.code + "\"}");
            return;
        }

        filterChain.doFilter(cachedRequest, response);
    }

    private AttestationFailure validate(CachedBodyHttpServletRequest request) {
        if (releaseManifestService == null) {
            return new AttestationFailure(HttpStatus.SERVICE_UNAVAILABLE, "LOCAL_RELEASE_ATTESTATION_UNAVAILABLE");
        }
        if (!releaseManifestService.isAuthorized()) {
            return new AttestationFailure(HttpStatus.SERVICE_UNAVAILABLE, "LOCAL_RELEASE_NOT_AUTHORIZED");
        }
        if (sharedSecret.isBlank()) {
            return new AttestationFailure(HttpStatus.SERVICE_UNAVAILABLE, "ATTESTATION_SECRET_NOT_CONFIGURED");
        }
        if (requireClientCertificate && !clientCertificateMatches(request)) {
            return new AttestationFailure(HttpStatus.FORBIDDEN, "CLIENT_CERTIFICATE_REQUIRED");
        }

        String remoteDigest = request.getHeader(RELEASE_DIGEST_HEADER);
        String timestamp = request.getHeader(RELEASE_TIMESTAMP_HEADER);
        String proof = request.getHeader(RELEASE_PROOF_HEADER);
        String serviceIdentity = request.getHeader(SERVICE_IDENTITY_HEADER);

        if (isBlank(remoteDigest) || isBlank(timestamp) || isBlank(proof) || isBlank(serviceIdentity)) {
            return new AttestationFailure(HttpStatus.FORBIDDEN, "REMOTE_RELEASE_ATTESTATION_MISSING");
        }
        if (!constantEquals(releaseManifestService.manifestDigest(), remoteDigest)) {
            return new AttestationFailure(HttpStatus.FORBIDDEN, "REMOTE_RELEASE_DIGEST_MISMATCH");
        }
        if (!timestampFresh(timestamp)) {
            return new AttestationFailure(HttpStatus.FORBIDDEN, "REMOTE_RELEASE_ATTESTATION_EXPIRED");
        }

        String expectedProof = hmacProof(
                request.getMethod(),
                request.getRequestURI(),
                serviceIdentity,
                remoteDigest,
                timestamp,
                sha256Hex(request.getCachedBody()));
        if (!constantEquals(expectedProof, proof)) {
            return new AttestationFailure(HttpStatus.FORBIDDEN, "REMOTE_RELEASE_PROOF_INVALID");
        }
        return null;
    }

    private boolean clientCertificateMatches(HttpServletRequest request) {
        Object attribute = request.getAttribute("jakarta.servlet.request.X509Certificate");
        if (!(attribute instanceof X509Certificate[] certificates) || certificates.length == 0) {
            return false;
        }
        if (requiredCertificateSubject.isBlank()) {
            return true;
        }
        String subject = certificates[0].getSubjectX500Principal().getName();
        return subject != null && subject.contains(requiredCertificateSubject);
    }

    private boolean timestampFresh(String value) {
        try {
            long epochSeconds = Long.parseLong(value);
            long now = Instant.now().getEpochSecond();
            return Math.abs(now - epochSeconds) <= maxClockSkewSeconds;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String hmacProof(
            String method,
            String path,
            String serviceIdentity,
            String digest,
            String timestamp,
            String bodyHash) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes(), "HmacSHA256"));
            String payload = method + "\n" + path + "\n" + serviceIdentity + "\n" + digest + "\n" + timestamp + "\n" + bodyHash;
            return "hmac-sha256:" + Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate release attestation proof", exception);
        }
    }

    private byte[] secretBytes() {
        try {
            return Base64.getDecoder().decode(sharedSecret);
        } catch (IllegalArgumentException ignored) {
            return sharedSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    private String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean constantEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record AttestationFailure(HttpStatus status, String code) {
    }
}
