package source.common.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MobileDownloadServiceTest {

    @Test
    void exposesMobileArtifactIntegrityMetadata() {
        MobileDownloadService service = new MobileDownloadService(
                "1.2.3",
                "42",
                "https://downloads.example/app.apk",
                "",
                "abc123",
                "",
                "cert456",
                "Fix one|Fix two");

        var info = service.releaseInfo();

        assertEquals("1.2.3", info.version());
        assertEquals("42", info.buildNumber());
        assertEquals("abc123", info.artifacts().get("android").sha256());
        assertEquals(2, info.changelog().size());
    }
}
