package com.kerosene.admin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Builds public mobile release metadata from configuration, preserving artifact checksums and signing identity.
 * Changelog configuration uses a pipe delimiter and is normalized into an immutable list.
 */
@Service
public class MobileDownloadService {

    /** User-facing semantic version of the current release. */
    private final String version;
    /** Platform build identifier for the release. */
    private final String buildNumber;
    /** Android artifact download URL. */
    private final String androidUrl;
    /** iOS artifact download URL. */
    private final String iosUrl;
    /** Expected SHA-256 checksum for the Android artifact. */
    private final String androidSha256;
    /** Expected SHA-256 checksum for the iOS artifact. */
    private final String iosSha256;
    /** Certificate fingerprint expected to sign the distributed artifacts. */
    private final String signingCertificateSha256;
    /** Immutable release notes parsed from the configured pipe-separated value. */
    private final List<String> changelog;

    /**
     * Binds mobile release version, artifact locations, integrity metadata, and changelog configuration.
     * Values are trimmed; the changelog is split on vertical bars and empty entries are discarded.
     *
     * @param version semantic release version
     * @param buildNumber platform build number
     * @param androidUrl Android package URL
     * @param iosUrl iOS package URL
     * @param androidSha256 Android package checksum
     * @param iosSha256 iOS package checksum
     * @param signingCertificateSha256 release signing certificate fingerprint
     * @param changelog pipe-separated release note entries
     */
    public MobileDownloadService(
            @Value("${mobile.release.version:${MOBILE_RELEASE_VERSION:1.0.0}}") String version,
            @Value("${mobile.release.build-number:${MOBILE_RELEASE_BUILD_NUMBER:1}}") String buildNumber,
            @Value("${mobile.release.android-url:${MOBILE_ANDROID_URL:}}") String androidUrl,
            @Value("${mobile.release.ios-url:${MOBILE_IOS_URL:}}") String iosUrl,
            @Value("${mobile.release.android-sha256:${MOBILE_ANDROID_SHA256:80158a61b982eb4db95cd010d63ca3d5b52d3e2215c8d9df046a6609db960582}}") String androidSha256,
            @Value("${mobile.release.ios-sha256:${MOBILE_IOS_SHA256:}}") String iosSha256,
            @Value("${mobile.release.signing-certificate-sha256:${MOBILE_SIGNING_CERT_SHA256:}}") String signingCertificateSha256,
            @Value("${mobile.release.changelog:${MOBILE_CHANGELOG:Initial Android release with secure wallet, passkeys, payment links, and web admin integration.}}") String changelog) {
        this.version = trim(version);
        this.buildNumber = trim(buildNumber);
        this.androidUrl = trim(androidUrl);
        this.iosUrl = trim(iosUrl);
        this.androidSha256 = trim(androidSha256);
        this.iosSha256 = trim(iosSha256);
        this.signingCertificateSha256 = trim(signingCertificateSha256);
        this.changelog = Arrays.stream(changelog.split("\\|"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    /**
     * Creates a release snapshot timestamped at call time with both platform artifact records.
     *
     * @return immutable metadata for public and administrative release endpoints
     */
    public MobileReleaseInfo releaseInfo() {
        return new MobileReleaseInfo(
                version,
                buildNumber,
                Map.of(
                        "android", new ArtifactInfo(androidUrl, androidSha256, signingCertificateSha256),
                        "ios", new ArtifactInfo(iosUrl, iosSha256, signingCertificateSha256)),
                changelog,
                Instant.now(),
                "Verify SHA-256 and signing certificate before installing side-loaded artifacts.");
    }

    /** Trims a configuration value and maps null to the empty string. */
    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Complete mobile release response, including version, platform artifacts, notes, and integrity guidance.
     *
     * @param version semantic release version
     * @param buildNumber platform build identifier
     * @param artifacts platform-keyed package download and integrity metadata
     * @param changelog release notes in presentation order
     * @param generatedAt instant when this response projection was created
     * @param integrityInstructions client guidance for checking package integrity before installation
     */
    public record MobileReleaseInfo(
            String version,
            String buildNumber,
            Map<String, ArtifactInfo> artifacts,
            List<String> changelog,
            Instant generatedAt,
            String integrityInstructions) {
    }

    /**
     * Download and authenticity metadata for a single platform package.
     *
     * @param url package download location, possibly blank when unavailable
     * @param sha256 expected package checksum
     * @param signingCertificateSha256 expected release signing certificate fingerprint
     */
    public record ArtifactInfo(String url, String sha256, String signingCertificateSha256) {
    }
}
