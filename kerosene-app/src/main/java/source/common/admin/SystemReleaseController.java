package source.common.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import source.common.release.ReleaseManifestService;

@RestController
public class SystemReleaseController {

    private final ReleaseManifestService releaseManifestService;

    public SystemReleaseController(ReleaseManifestService releaseManifestService) {
        this.releaseManifestService = releaseManifestService;
    }

    @GetMapping("/system/release")
    public ReleaseManifestService.ReleaseSnapshot release() {
        return releaseManifestService.snapshot();
    }
}
