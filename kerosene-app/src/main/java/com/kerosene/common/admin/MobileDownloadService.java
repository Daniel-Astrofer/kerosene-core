package source.common.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class MobileDownloadService {

    private final String version;
    private final String buildNumber;
    private final String androidUrl;
    private final String iosUrl;
    private final String androidSha256;
    private final String iosSha256;
    private final String signingCertificateSha256;
    private final List<String> changelog;

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

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public record MobileReleaseInfo(
            String version,
            String buildNumber,
            Map<String, ArtifactInfo> artifacts,
            List<String> changelog,
            Instant generatedAt,
            String integrityInstructions) {
    }

    public record ArtifactInfo(String url, String sha256, String signingCertificateSha256) {
    }
}
